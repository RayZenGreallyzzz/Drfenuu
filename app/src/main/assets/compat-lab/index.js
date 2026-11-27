"use strict";
const $=id=>document.getElementById(id);
function row(name,detail){const li=document.createElement("li");li.textContent=name+" — "+detail;$("results").append(li);}
const deadline=(promise,ms=3500)=>new Promise((resolve,reject)=>{const t=setTimeout(()=>reject(Error("тайм-аут")),ms);Promise.resolve(promise).then(v=>{clearTimeout(t);resolve(v);},e=>{clearTimeout(t);reject(e);});});
async function test(name,fn){try{await deadline(Promise.resolve().then(fn));row(name,"OK");}catch(e){row(name,"Ошибка: "+String(e).slice(0,180));}}
$("run").onclick=async()=>{
 $("run").disabled=true;$("results").replaceChildren();
 try{
  row("Manifest",String(browser.runtime.getManifest().manifest_version));
  await test("Background runtime messaging",async()=>{const r=await browser.runtime.sendMessage({type:"ping"});if(r?.pong!==true)throw Error("нет ответа");});
  await test("storage.local",async()=>{const key="compat-test-"+Date.now();try{await browser.storage.local.set({[key]:"test"});const r=await browser.storage.local.get(key);if(r[key]!=="test")throw Error("несовпадение");}finally{await browser.storage.local.remove(key);}});
  await test("Web Worker",()=>new Promise((resolve,reject)=>{const w=new Worker("worker.js");const timeout=setTimeout(()=>{w.terminate();reject(Error("тайм-аут"));},2500);w.onmessage=e=>{clearTimeout(timeout);w.terminate();e.data===42?resolve():reject(Error("неверный ответ"));};w.onerror=()=>{clearTimeout(timeout);w.terminate();reject(Error("worker error"));};w.postMessage(21);}));
  await test("WebAssembly",async()=>{const r=await WebAssembly.instantiate(new Uint8Array([0,97,115,109,1,0,0,0]));if(!r.instance)throw Error("нет instance");});
  await test("Blob / ArrayBuffer",async()=>{const b=new Blob([new Uint8Array([1,2,3])]);const a=new Uint8Array(await b.arrayBuffer());if(a.length!==3||a[2]!==3)throw Error("несовпадение");});
  row("Downloads API",typeof browser.downloads?.download==="function"?"виден без разрешения":"недоступен в тесте без разрешения downloads; не доказательство отсутствия в движке");
  row("Service worker",typeof navigator.serviceWorker==="object"?"интерфейс страницы виден; фон MV3 проверен отдельно":"интерфейс страницы не виден");
 }finally{$("run").disabled=false;}
};
$("download").onclick=()=>{const url=URL.createObjectURL(new Blob(["AetherBolt local compatibility test\n"],{type:"text/plain"}));const a=document.createElement("a");a.href=url;a.download="aetherbolt-compat-test.txt";document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),30000);$("status").textContent="Запрос сохранения отправлен. Проверьте появление файла в загрузках.";};
