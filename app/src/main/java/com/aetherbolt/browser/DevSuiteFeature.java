package com.aetherbolt.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.function.BiConsumer;
import org.json.JSONObject;
import org.mozilla.geckoview.*;

final class DevSuiteFeature {
    static final String BRIDGE_ID="devsuite@aetherbolt.local", TOOLS_ID="devsuite-tools@aetherbolt.local";
    private final Activity ui;
    private final GeckoRuntime runtime;
    private final Supplier<GeckoSession> current;
    private final BiConsumer<WebExtension,String> open;
    private final Set<GeckoSession> sessions=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<GeckoSession,WebExtension.Port> ports=new IdentityHashMap<>();
    private WebExtension bridge,tools;
    private WebExtension.Port control;
    private boolean running;
    DevSuiteFeature(Activity ui,GeckoRuntime runtime,Supplier<GeckoSession> current,BiConsumer<WebExtension,String> open){this.ui=ui;this.runtime=runtime;this.current=current;this.open=open;}
    private void message(String msg){if(running&&!ui.isDestroyed())Toast.makeText(ui,msg,Toast.LENGTH_LONG).show();}
    private final WebExtension.PortDelegate receiver=new WebExtension.PortDelegate(){
        @Override public void onPortMessage(Object msg,WebExtension.Port p){if(msg instanceof JSONObject && (p==control||p==ports.get(current.get()))){JSONObject j=(JSONObject)msg;if("result".equals(j.optString("type")))message(j.optString("message","Готово"));}}
        @Override public void onDisconnect(WebExtension.Port p){if(control==p)control=null;ports.values().removeIf(v->v==p);}
    };
    void start(){
        running=true;WebExtensionController c=runtime.getWebExtensionController();
        c.ensureBuiltIn("resource://android/assets/devsuite/bridge/",BRIDGE_ID).then(e->{ui.runOnUiThread(()->{if(!running)return;bridge=e;
            e.setMessageDelegate(new WebExtension.MessageDelegate(){@Override public void onConnect(WebExtension.Port p){if(!running||p.sender.session!=null){p.disconnect();return;}WebExtension.Port old=control;control=p;p.setDelegate(receiver);if(old!=null&&old!=p)old.disconnect();}},"devsuite_control");
            for(GeckoSession s:sessions)bind(s);});return null;},err->{ui.runOnUiThread(()->message("DevSuite: "+err));return null;});
        c.ensureBuiltIn("resource://android/assets/devsuite/tools/",TOOLS_ID).then(e->{ui.runOnUiThread(()->{if(running)tools=e;});return null;},err->{ui.runOnUiThread(()->message("Локальные инструменты: "+err));return null;});
    }
    void attach(GeckoSession s){sessions.add(s);if(bridge!=null)bind(s);}
    private void bind(GeckoSession s){s.getWebExtensionController().setMessageDelegate(bridge,new WebExtension.MessageDelegate(){
        @Override public void onConnect(WebExtension.Port p){if(!running||!sessions.contains(s)||p.sender.session!=s||!p.sender.isTopLevel()){p.disconnect();return;}WebExtension.Port old=ports.put(s,p);p.setDelegate(receiver);if(old!=null&&old!=p)old.disconnect();}
    },"devsuite_tab");}
    void detach(GeckoSession s){sessions.remove(s);if(bridge!=null)s.getWebExtensionController().setMessageDelegate(bridge,null,"devsuite_tab");WebExtension.Port p=ports.remove(s);if(p!=null)p.disconnect();}
    void stop(){running=false;for(GeckoSession s:new ArrayList<>(sessions))detach(s);if(bridge!=null)bridge.setMessageDelegate(null,"devsuite_control");WebExtension.Port p=control;control=null;if(p!=null)p.disconnect();}
    private void send(String type){WebExtension.Port p=ports.get(current.get());if(p==null){message("Откройте HTTP(S)-страницу и при необходимости обновите её.");return;}try{p.postMessage(new JSONObject().put("type",type));}catch(Exception e){message("DevSuite: "+e);}}
    void menu(){new AlertDialog.Builder(ui).setTitle("DevSuite [ >_ ]").setItems(new String[]{"Eruda — инспектор","xterm — JavaScript-консоль","CyberChef — локальная вкладка","Linux v86 — локальная ВМ","Прокси SOCKS5 / HTTPS"},(d,n)->{switch(n){case 0:send("eruda");break;case 1:send("xterm");break;case 2:open.accept(tools,"cyberchef/CyberChef_v11.6.0.html");break;case 3:open.accept(tools,"v86/index.html");break;case 4:proxy();break;}}).setNegativeButton("Закрыть",null).show();}
    private void proxy(){new AlertDialog.Builder(ui).setTitle("Прокси браузера").setMessage("Общий для GeckoView. Не VPN; без авторизации. При разрыве канала расширения настройка может сброситься.").setItems(new String[]{"Прямое соединение","SOCKS5","HTTPS"},(d,n)->{if(n==0)sendProxy("direct","",0);else proxyForm(n==1?"socks":"https");}).show();}
    private void proxyForm(String type){
        LinearLayout box=new LinearLayout(ui);box.setOrientation(LinearLayout.VERTICAL);EditText host=new EditText(ui),port=new EditText(ui);host.setSingleLine();host.setHint("Адрес сервера");port.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);port.setText(type.equals("socks")?"1080":"443");box.addView(host);box.addView(port);
        AlertDialog dialog=new AlertDialog.Builder(ui).setTitle(type).setView(box).setNegativeButton("Отмена",null).setPositiveButton("Подключить",null).create();dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String h=host.getText().toString().trim();try{int p=Integer.parseInt(port.getText().toString());if(h.isEmpty()||!h.matches("[a-zA-Z0-9.:-]+")||p<1||p>65535)throw new IllegalArgumentException();sendProxy(type,h,p);dialog.dismiss();}catch(Exception e){host.setError("Проверьте адрес и порт");}});
    }
    private void sendProxy(String type,String host,int port){if(control==null){message("Канал прокси недоступен");return;}try{control.postMessage(new JSONObject().put("type","proxy").put("proxy",new JSONObject().put("type",type).put("host",host).put("port",port)));}catch(Exception e){message("Прокси: "+e);}}
}
