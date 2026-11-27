// Passive connection. No overlay, page event handlers, or automatic script execution.
(() => {
  if (window !== window.top || !/^https?:$/.test(location.protocol)) return;
  let port;
  try { port = browser.runtime.connectNative("aetherScripts"); } catch (_) { return; }
  let running = false;
  port.onMessage.addListener(async request => {
    if (!request || request.type !== "run" || running || typeof request.token !== "string") return;
    const token = request.token;
    running = true;
    let result;
    try {
      if (request.origin !== location.origin || request.url !== location.href || typeof request.code !== "string" || request.code.length > 262144) throw Error("Страница изменилась");
      result = await browser.runtime.sendMessage({kind:"run-local",code:request.code,origin:request.origin,url:request.url});
      if (!result || result.ok !== true) throw Error(result && result.message || "Нет подтверждения выполнения");
      port.postMessage({type:"result",token,ok:true});
    } catch (err) {
      try { port.postMessage({type:"result",token,ok:false,message:String(err).slice(0,180)}); } catch (_) {}
    } finally { running = false; }
  });
})();
