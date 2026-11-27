browser.runtime.onMessage.addListener(async (request, sender) => {
  if (request?.kind !== "run-local" || !sender.tab || sender.frameId !== 0 ||
      typeof request.code !== "string" || request.code.length > 262144 ||
      typeof request.documentId !== "string" || !/^[a-f0-9]{32}$/.test(request.documentId) ||
      (request.automaticId != null && (typeof request.automaticId !== "string" || request.automaticId.length > 100))) {
    return {ok: false, message: "Недопустимый запрос"};
  }
  let origin;
  try {
    origin = new URL(sender.url).origin;
    if (origin !== request.origin || !/^https?:\/\//.test(origin) ||
        typeof request.url !== "string" || new URL(request.url).origin !== origin) {
      return {ok: false, changed: true, message: "Сайт изменился"};
    }
  } catch (_) {
    return {ok: false, changed: true, message: "Страница изменилась"};
  }

  const quote = JSON.stringify;
  const automaticId = quote(request.automaticId ?? null);
  const code = "(() => {\n" +
    "const context = globalThis.__aetherboltScriptContext;\n" +
    "if (location.origin !== " + quote(origin) + " || location.href !== " + quote(request.url) +
    " || context?.documentId !== " + quote(request.documentId) + ") return false;\n" +
    "const automaticId = " + automaticId + ";\n" +
    "if (automaticId !== null && context.automated.has(automaticId)) return true;\n" +
    "if (automaticId !== null) context.automated.add(automaticId);\n" +
    "(function () {\n" + request.code + "\n}).call(window);\nreturn true;\n})()";
  try {
    const values = await browser.tabs.executeScript(sender.tab.id, {code, frameId: 0, runAt: "document_idle"});
    return Array.isArray(values) && values[0] === true
      ? {ok: true}
      : {ok: false, changed: true, message: "Страница изменилась"};
  } catch (error) {
    return {ok: false, message: String(error).slice(0, 180)};
  }
});
