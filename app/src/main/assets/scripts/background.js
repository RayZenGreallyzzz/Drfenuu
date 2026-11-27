browser.runtime.onMessage.addListener(async (request,sender)=>{
  if(!request||request.kind!=="run-local"||!sender.tab||sender.frameId!==0||typeof request.code!=="string"||request.code.length>262144)return {ok:false,message:"Недопустимый запрос"};
  let origin;
  try { origin=new URL(sender.url).origin; } catch(e){return {ok:false,message:"Недопустимая страница"};}
  if(origin!==request.origin||!/^https?:\/\//.test(origin)||typeof request.url!=="string"||new URL(request.url).origin!==origin)return {ok:false,message:"Сайт изменился"};
  const code="if(location.origin==="+JSON.stringify(origin)+"&&location.href==="+JSON.stringify(request.url)+"){(function(){\n"+request.code+"\n}).call(window);true;}else{false;}";
  try { const values=await browser.tabs.executeScript(sender.tab.id,{code,frameId:0,runAt:"document_idle"});return {ok:Array.isArray(values)&&values[0]===true,message:"Страница изменилась или код не запущен"}; }
  catch(e){return {ok:false,message:String(e).slice(0,180)};}
});
