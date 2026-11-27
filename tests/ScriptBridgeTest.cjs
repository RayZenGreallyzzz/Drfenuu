const assert = require("node:assert/strict");
const {webcrypto} = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const assets = path.join(__dirname, "../app/src/main/assets/scripts");
let receive;
let currentFrame;
vm.runInNewContext(fs.readFileSync(path.join(assets, "background.js"), "utf8"), {
  URL,
  browser: {
    runtime: {onMessage: {addListener: listener => {receive = listener;}}},
    tabs: {executeScript: async (id, {code, frameId}) => {
      assert.equal(id, 7);
      assert.equal(frameId, 0);
      return [vm.runInContext(code, currentFrame)];
    }},
  },
});

function frame(url) {
  const events = new Map();
  const window = {addEventListener: (type, listener) => events.set(type, listener), runs: 0};
  window.top = window;
  const ports = [];
  const location = new URL(url);
  const context = vm.createContext({window, location, crypto: webcrypto, setTimeout, clearTimeout, browser: {
    runtime: {
      connectNative(name) {
        assert.equal(name, "aetherScripts");
        const port = {
          messages: [],
          onMessage: {addListener(listener) {port.deliver = listener;}},
          onDisconnect: {addListener() {}},
          postMessage(message) {port.messages.push(message);},
          disconnect() {},
        };
        ports.push(port);
        return port;
      },
      sendMessage(request) {
        return receive(request, {tab: {id: 7}, frameId: 0, url});
      },
    },
  }});
  vm.runInContext(fs.readFileSync(path.join(assets, "content.js"), "utf8"), context);
  return {window, ports, context, events, location};
}

async function test() {
  const first = frame("https://example.org/models/a");
  const port = first.ports[0];
  const ready = port.messages[0];
  assert.equal(ready.type, "ready");
  assert.match(ready.documentId, /^[a-f0-9]{32}$/);
  await port.deliver({type: "hello"});
  assert.equal(port.messages.at(-1).documentId, ready.documentId);
  currentFrame = first.context;
  const request = {
    type: "run", token: "test", origin: "https://example.org", url: first.location.href,
    documentId: ready.documentId, code: "window.runs++;", automaticId: "demo",
  };
  await port.deliver(request);
  assert.equal(first.window.runs, 1);
  assert.equal(port.messages.at(-1).ok, true);
  await port.deliver(request);
  assert.equal(first.window.runs, 1);

  await port.deliver({...request, automaticId: null});
  assert.equal(first.window.runs, 2);
  await port.deliver({...request, url: "https://example.org/models/b", automaticId: "wrong-path"});
  assert.equal(port.messages.at(-1).changed, true);
  assert.equal(first.window.runs, 2);
  const invalid = await receive({...request, kind: "run-local", origin: "https://other.example"},
    {tab: {id: 7}, frameId: 0, url: first.location.href});
  assert.equal(invalid.ok, false);

  const second = frame(first.location.href);
  currentFrame = second.context;
  await port.deliver({...request, automaticId: "stale"});
  assert.equal(second.window.runs, 0);
  assert.equal(port.messages.at(-1).changed, true);
  const nextPort = second.ports[0];
  await nextPort.deliver({...request, documentId: nextPort.messages[0].documentId});
  assert.equal(second.window.runs, 1);

  first.events.get("pagehide")();
  first.events.get("pageshow")({persisted: true});
  currentFrame = first.context;
  const restored = first.ports[1];
  assert.equal(restored.messages[0].documentId, ready.documentId);
  await restored.deliver(request);
  assert.equal(first.window.runs, 2);
  console.log("PASS: script bridge authorization, manual launch, reload and back/forward cache");
}

test().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
