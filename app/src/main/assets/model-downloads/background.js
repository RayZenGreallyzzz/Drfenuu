"use strict";

const modelSuffix = /\.(glb|gltf|fbx|obj|stl|ply|dae|3mf|usdz|usd|usda|usdc|blend|3ds|off|drc)$/i;
const contentTypes = new Map([
  ["model/gltf-binary", "glb"], ["model/gltf+json", "gltf"], ["model/stl", "stl"],
  ["model/obj", "obj"], ["model/vnd.usdz+zip", "usdz"], ["model/vnd.collada+xml", "dae"],
]);
const filter = {urls: ["http://*/*", "https://*/*"]};
let queue = Promise.resolve();
function update(callback) {
  const task = queue.then(async () => {
    const {capture = {tabId: null, files: []}} = await browser.storage.session.get("capture");
    return callback(capture);
  });
  queue = task.catch(console.error);
  return task;
}

async function save(capture) {
  await browser.storage.session.set({capture});
  await browser.action.setBadgeText({text: capture.files.length ? String(capture.files.length) : capture.tabId === null ? "" : "ON"});
}

browser.webRequest.onHeadersReceived.addListener(details => {
  if (details.tabId < 0 || details.incognito || details.method !== "GET" ||
      details.statusCode < 200 || details.statusCode >= 300) return;
  let url;
  try { url = new URL(details.url); } catch (_) { return; }
  if (url.username || url.password) return;
  const type = details.responseHeaders?.find(header => header.name.toLowerCase() === "content-type")?.value;
  let name = url.pathname.split("/").pop() || "";
  try { name = decodeURIComponent(name); } catch (_) {}
  if (!modelSuffix.test(name)) {
    const extension = contentTypes.get((type || "").split(";")[0].trim().toLowerCase());
    if (!extension) return;
    name = (name || "model") + "." + extension;
  }
  name = name.replace(/[\x00-\x1f/\\:*?"<>|]/g, "_").slice(-100);
  update(async capture => {
    if (capture.tabId !== details.tabId || capture.files.some(file => file.url === url.href)) return;
    capture.files = [...capture.files.slice(-79), {id: crypto.randomUUID(), name, host: url.hostname, url: url.href}];
    await save(capture);
  });
}, filter, ["responseHeaders"]);

browser.tabs.onRemoved.addListener(tabId => update(async capture => {
  if (tabId === capture.tabId) await save({tabId: null, files: capture.files});
}));

browser.runtime.onMessage.addListener((message, sender) => {
  if (sender.url !== browser.runtime.getURL("popup.html")) return undefined;
  return update(async capture => {
    switch (message?.type) {
      case "state":
        return capture;
      case "start": {
        const [tab] = await browser.tabs.query({active: true, currentWindow: true});
        if (!tab || tab.incognito || !/^https?:\/\//.test(tab.url || "")) {
          throw new Error("Откройте страницу с моделью в обычной вкладке.");
        }
        if (capture.tabId !== tab.id) {
          capture = {tabId: tab.id, files: []};
          await save(capture);
        }
        return capture;
      }
      case "stop":
        capture.tabId = null;
        await save(capture);
        return capture;
      case "download": {
        const file = capture.files.find(entry => entry.id === message.id);
        if (!file) throw new Error("Файл больше недоступен в списке.");
        await browser.downloads.download({url: file.url, filename: file.name, saveAs: true});
        return capture;
      }
      default:
        throw new Error("Неизвестное действие.");
    }
  });
});
