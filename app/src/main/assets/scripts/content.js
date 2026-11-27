// Passive, bounded retry of the existing native port. No automatic script execution.
(() => {
  if (window !== window.top || !/^https?:$/.test(location.protocol)) return;
  let port=null, timer=null, closed=false, running=false;
  function retry(){if(!closed&&!timer)timer=setTimeout(()=>{timer=null;connect();},3000);}
  function connect(){
    if(closed||port)return;
    let p;
    try{p=browser.runtime.connectNative("aetherScripts");port=p;}catch(_){retry();return;}
    p.onDisconnect.addListener(()=>{if(port===p){port=null;retry();}});
    p.onMessage.addListener(async request=>{
      if(port!==p||closed||!request||request.type!=="run"||running||typeof request.token!=="string")return;
      const token=request.token;running=true;
      try{
        if(request.origin!==location.origin||request.url!==location.href||typeof request.code!=="string"||request.code.length>262144)throw Error("Страница изменилась");
        const result=await browser.runtime.sendMessage({kind:"run-local",code:request.code,origin:request.origin,url:request.url});
        if(!result||result.ok!==true)throw Error(result&&result.message||"Нет подтверждения выполнения");
        if(port===p&&!closed)p.postMessage({type:"result",token,ok:true});
      }catch(err){try{if(port===p&&!closed)p.postMessage({type:"result",token,ok:false,message:String(err).slice(0,180)});}catch(_){}}
      finally{running=false;}
    });
  }
  window.addEventListener("pagehide",()=>{closed=true;clearTimeout(timer);timer=null;const p=port;port=null;try{p?.disconnect();}catch(_){}});
  window.addEventListener("pageshow",e=>{if(e.persisted){closed=false;connect();}});
  connect();
})();
