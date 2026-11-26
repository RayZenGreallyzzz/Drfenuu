"use strict";
let proxy=null;
const listener=()=>({...proxy});
const setProxy=p=>{
 if(!browser.proxy?.onRequest)throw Error("Прокси API недоступен");
 let next=null;
 if(p.type!=="direct"){if(!["socks","https"].includes(p.type)||!/^[a-zA-Z0-9.:-]+$/.test(p.host)||!Number.isInteger(p.port)||p.port<1||p.port>65535)throw Error("Неверный прокси");next={type:p.type,host:p.host,port:p.port,...(p.type==="socks"?{proxyDNS:true}:{})};}
 const ev=browser.proxy.onRequest;if(ev.hasListener(listener))ev.removeListener(listener);proxy=next;if(proxy)ev.addListener(listener,{urls:["http://*/*","https://*/*"]});
};
function connect(){let p;try{p=browser.runtime.connectNative("devsuite_control");}catch(e){setTimeout(connect,3000);return;}p.onDisconnect.addListener(()=>{try{setProxy({type:"direct"});}catch(e){}setTimeout(connect,3000);});p.onMessage.addListener(m=>{if(m.type!=="proxy")return;try{setProxy(m.proxy);p.postMessage({type:"result",message:proxy?"Прокси настроен (соединение не проверено)":"Прямое соединение"});}catch(e){p.postMessage({type:"result",message:String(e)});}});}
connect();
browser.runtime.onMessage.addListener((m,s)=>{
 if(s.id!==browser.runtime.id||s.frameId!==0||!Number.isInteger(s.tab?.id)||!["eruda","xterm"].includes(m?.type)||!/^https?:/.test(s.url||""))return;
 return (async()=>{const target={tabId:s.tab.id,frameIds:[0]};if(m.type==="xterm")await browser.scripting.insertCSS({target,files:["vendor/xterm.css"]});const result=await browser.scripting.executeScript({target,world:"MAIN",files:m.type==="xterm"?["vendor/xterm.js","inject_xterm.js"]:["vendor/eruda.js","inject_eruda.js"]});const err=result?.find(v=>v.error);if(err)throw Error(String(err.error));return {message:"Инструмент переключён"};})().catch(e=>({message:String(e)}));
});
