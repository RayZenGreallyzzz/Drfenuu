"use strict";
let vm=null,term=null,generation=0;
const $=id=>document.getElementById(id);
async function stop(){
  generation++;const old=vm;vm=null;$("start").disabled=true;
  if(term){term.dispose();term=null;}
  try{if(old)await old.destroy();}catch(e){}finally{$("status").textContent="Остановлено";$("start").disabled=false;}
}
$("start").onclick=async()=>{
  if(vm||$("start").disabled)return;const ticket=++generation;$("start").disabled=true;$("status").textContent="Загрузка…";
  try{
    const file=$("iso").files[0];if(file&&file.size>256*1024*1024)throw Error("ISO не больше 256 МБ");
    const cdrom=file?{buffer:await file.arrayBuffer()}:{url:"linux.iso"};
    if(ticket!==generation)return;
    term=new Terminal({fontSize:13,convertEol:false,scrollback:500,cols:60,rows:12});term.open($("terminal"));
    const instance=new V86({wasm_path:"v86.wasm",memory_size:128*1024*1024,vga_memory_size:4*1024*1024,screen_container:$("screen"),bios:{url:"seabios.bin"},vga_bios:{url:"vgabios.bin"},cdrom,autostart:true});
    vm=instance;instance.add_listener("serial0-output-byte",b=>{if(vm===instance)term?.write(new Uint8Array([b]));});instance.add_listener("emulator-ready",()=>{if(vm===instance)$("status").textContent="VM готова — дождитесь загрузки Linux";});
    term.onData(data=>{if(vm===instance)vm.serial0_send(data);});
  }catch(e){if(ticket===generation){await stop();$("status").textContent=String(e);}}
};
$("stop").onclick=stop;$("kbd").onclick=()=>$("command").focus();
function key(code,serial){if(!vm)return;if($("mode").value==="serial")vm.serial0_send(serial);else vm.keyboard_send_scancodes([code,code|128]);}
$("enter").onclick=()=>key(0x1c,"\r");$("esc").onclick=()=>key(1,"\x1b");$("backspace").onclick=()=>key(0x0e,"\x7f");
$("send").onsubmit=e=>{e.preventDefault();if(!vm)return;const input=$("command");if($("mode").value==="serial")vm.serial0_send(input.value+"\r");else{vm.keyboard_send_text(input.value);key(0x1c,"\r");}input.value="";};
window.addEventListener("pagehide",stop);
