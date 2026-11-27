(() => {
  if (window !== window.top || !/^https?:$/.test(location.protocol)) return;

  const documentId = Array.from(crypto.getRandomValues(new Uint8Array(16)),
    byte => byte.toString(16).padStart(2, "0")).join("");
  // This state lives in the extension's isolated world for this frame.
  globalThis.__aetherboltScriptContext = {documentId, automated: new Set()};
  let port = null;
  let timer = null;
  let closed = false;
  let running = false;

  function retry() {
    if (!closed && timer === null) {
      timer = setTimeout(() => {
        timer = null;
        connect();
      }, 3000);
    }
  }

  function connect() {
    if (closed || port) return;
    let connection;
    try {
      connection = browser.runtime.connectNative("aetherScripts");
      port = connection;
      connection.onDisconnect.addListener(() => {
        if (port === connection) {
          port = null;
          retry();
        }
      });
      connection.onMessage.addListener(async request => {
        if (port !== connection || closed) return;
        if (request?.type === "hello") {
          try { connection.postMessage({type: "ready", documentId}); } catch (_) {}
          return;
        }
        if (request?.type !== "run" || typeof request.token !== "string") return;
        const result = {type: "result", token: request.token, documentId,
          automaticId: request.automaticId, url: request.url, ok: false};
        if (running) {
          try { connection.postMessage({...result, message: "Страница уже выполняет скрипт"}); } catch (_) {}
          return;
        }
        running = true;
        try {
          if (request.documentId !== documentId || request.origin !== location.origin ||
              request.url !== location.href) {
            result.changed = true;
            result.message = "Страница изменилась";
          } else {
            Object.assign(result, await browser.runtime.sendMessage({
              kind: "run-local", code: request.code, origin: request.origin, url: request.url,
              documentId, automaticId: request.automaticId,
            }));
          }
        } catch (error) {
          result.message = String(error).slice(0, 180);
        } finally {
          running = false;
        }
        if (port === connection && !closed) {
          try { connection.postMessage(result); } catch (_) {}
        }
      });
      connection.postMessage({type: "ready", documentId});
    } catch (_) {
      port = null;
      try { connection?.disconnect(); } catch (_) {}
      retry();
    }
  }

  window.addEventListener("pagehide", () => {
    closed = true;
    clearTimeout(timer);
    timer = null;
    const connection = port;
    port = null;
    try { connection?.disconnect(); } catch (_) {}
  });
  window.addEventListener("pageshow", event => {
    if (event.persisted) {
      closed = false;
      connect();
    }
  });
  connect();
})();
