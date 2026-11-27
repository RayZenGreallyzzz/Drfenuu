package com.aetherbolt.browser;

import android.app.Activity;
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
    private boolean running,loadingTools;
    private String captureToken;
    private WebExtension.Port capturePort;
    private java.util.function.Consumer<JSONObject> captureCallback;
    private final android.os.Handler captureHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable captureTimeout=()->finishCapture(null);
    private void finishCapture(JSONObject data){java.util.function.Consumer<JSONObject> cb=captureCallback;captureCallback=null;captureToken=null;capturePort=null;captureHandler.removeCallbacks(captureTimeout);if(cb!=null)cb.accept(data);}
    void capture(java.util.function.Consumer<JSONObject> callback){
        if(captureCallback!=null){callback.accept(null);return;}
        WebExtension.Port p=ports.get(current.get());if(!running||p==null){callback.accept(null);return;}
        captureToken=UUID.randomUUID().toString();capturePort=p;captureCallback=callback;
        captureHandler.postDelayed(captureTimeout,8000);
        try{p.postMessage(new JSONObject().put("type","offline_capture").put("token",captureToken));}catch(Exception e){finishCapture(null);}
    }
    DevSuiteFeature(Activity ui,GeckoRuntime runtime,Supplier<GeckoSession> current,BiConsumer<WebExtension,String> open){this.ui=ui;this.runtime=runtime;this.current=current;this.open=open;}
    private void message(String msg){if(running&&!ui.isDestroyed())Toast.makeText(ui,msg,Toast.LENGTH_LONG).show();}
    private final WebExtension.PortDelegate receiver=new WebExtension.PortDelegate(){
        @Override public void onPortMessage(Object msg,WebExtension.Port p){if(msg instanceof JSONObject&&p==capturePort&&captureToken!=null){JSONObject data=(JSONObject)msg;if("offline_capture".equals(data.optString("type"))&&captureToken.equals(data.optString("token"))){finishCapture(p==ports.get(current.get())?data:null);return;}}if(msg instanceof JSONObject && (p==control||p==ports.get(current.get()))){JSONObject j=(JSONObject)msg;if("result".equals(j.optString("type")))message(j.optString("message","Готово"));}}
        @Override public void onDisconnect(WebExtension.Port p){if(capturePort==p)finishCapture(null);if(control==p)control=null;ports.values().removeIf(v->v==p);}
    };
    void start(){
        running=true;WebExtensionController c=runtime.getWebExtensionController();
        c.ensureBuiltIn("resource://android/assets/devsuite/bridge/",BRIDGE_ID).then(e->{ui.runOnUiThread(()->{if(!running)return;bridge=e;
            e.setMessageDelegate(new WebExtension.MessageDelegate(){@Override public void onConnect(WebExtension.Port p){if(!running||p.sender.session!=null){p.disconnect();return;}WebExtension.Port old=control;control=p;p.setDelegate(receiver);if(old!=null&&old!=p)old.disconnect();}},"devsuite_control");
            for(GeckoSession s:sessions)bind(s);});return null;},err->{ui.runOnUiThread(()->message("DevSuite: "+err));return null;});

    }
    void attach(GeckoSession s){sessions.add(s);if(bridge!=null)bind(s);}
    private void bind(GeckoSession s){s.getWebExtensionController().setMessageDelegate(bridge,new WebExtension.MessageDelegate(){
        @Override public void onConnect(WebExtension.Port p){if(!running||!sessions.contains(s)||p.sender.session!=s||!p.sender.isTopLevel()){p.disconnect();return;}WebExtension.Port old=ports.put(s,p);p.setDelegate(receiver);if(old!=null&&old!=p)old.disconnect();}
    },"devsuite_tab");}
    void detach(GeckoSession s){sessions.remove(s);if(bridge!=null)s.getWebExtensionController().setMessageDelegate(bridge,null,"devsuite_tab");WebExtension.Port p=ports.remove(s);if(p!=null)p.disconnect();}
    void stop(){running=false;finishCapture(null);for(GeckoSession s:new ArrayList<>(sessions))detach(s);if(bridge!=null)bridge.setMessageDelegate(null,"devsuite_control");WebExtension.Port p=control;control=null;if(p!=null)p.disconnect();}
    void send(String type){WebExtension.Port p=ports.get(current.get());if(p==null){message("Откройте HTTP(S)-страницу и при необходимости обновите её.");return;}try{p.postMessage(new JSONObject().put("type",type));}catch(Exception e){message("DevSuite: "+e);}}
    void openTool(String page){
        if(tools!=null){open.accept(tools,page);return;}
        if(loadingTools){message("Локальные инструменты загружаются");return;}
        loadingTools=true;message("Подготовка локального инструмента");
        runtime.getWebExtensionController().ensureBuiltIn("resource://android/assets/devsuite/tools/",TOOLS_ID).then(e->{ui.runOnUiThread(()->{loadingTools=false;if(running&&!ui.isDestroyed()){tools=e;open.accept(e,page);}});return null;},err->{ui.runOnUiThread(()->{loadingTools=false;message("Локальные инструменты: "+err);});return null;});
    }
    void sendProxy(String type,String host,int port){if(control==null){message("Канал прокси недоступен");return;}try{control.postMessage(new JSONObject().put("type","proxy").put("proxy",new JSONObject().put("type",type).put("host",host).put("port",port)));}catch(Exception e){message("Прокси: "+e);}}
}
