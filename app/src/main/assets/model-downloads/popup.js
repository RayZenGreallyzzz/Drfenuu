"use strict";
const status = document.getElementById("status");
const error = document.getElementById("error");
const list = document.getElementById("files");
const start = document.getElementById("start");
const stop = document.getElementById("stop");

function render({tabId, files}) {
  stop.disabled = tabId === null;
  status.textContent = tabId === null
    ? (files.length ? `Файлов найдено: ${files.length}` : "Запустите поиск на странице с моделью.")
    : "Поиск включён. Перезагрузите страницу или откройте модель на этой вкладке.";
  list.replaceChildren(...files.map(file => {
    const row = document.createElement("li");
    const label = document.createElement("span");
    label.textContent = file.name + " · " + file.host;
    const button = document.createElement("button");
    button.textContent = "Скачать";
    button.onclick = () => request("download", file.id);
    row.append(label, button);
    return row;
  }));
}

async function request(type, id) {
  error.textContent = "";
  try {
    render(await browser.runtime.sendMessage({type, id}));
  } catch (failure) {
    error.textContent = failure.message;
  }
}

start.onclick = () => request("start");
stop.onclick = () => request("stop");
browser.storage.onChanged.addListener((changes, area) => {
  if (area === "session" && changes.capture?.newValue) render(changes.capture.newValue);
});
request("state");
