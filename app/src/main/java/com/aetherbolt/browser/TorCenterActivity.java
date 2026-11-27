package com.aetherbolt.browser;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.text.Html;
import android.view.*;
import android.widget.*;
import org.torproject.jni.TorService;
import java.net.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

public final class TorCenterActivity extends Activity {
    private static final int BG=0xff0b1020,TEXT=0xffedf2ff;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final AtomicReference<Socket> activeSocket=new AtomicReference<>();
    private volatile int generation;
    private boolean bound,busy;
    private TorService service;
    private TextView status,result;
    private EditText address;
    private Button start,stop,check,read;
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){
            if(!bound)return;
            service=((TorService.LocalBinder)binder).getService();
            status.setText("Tor запущен; строится цепочка…");update();
        }
        @Override public void onServiceDisconnected(ComponentName name){stopTor("Сервис Tor отключён");}
        @Override public void onBindingDied(ComponentName name){stopTor("Связь с сервисом потеряна");}
        @Override public void onNullBinding(ComponentName name){stopTor("Сервис недоступен");}
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(24),dp(16),dp(24));scroll.addView(c);
        TextView title=text("Tor Engine • эксперимент",24);c.addView(title);
        c.addView(text("Встроенный клиент Tor. Здесь только проверка соединения и текст .onion. Это не полноценный Tor Browser и не гарантия анонимности. Обычные вкладки AetherBolt не перенаправляются через Tor.",14));
        status=text("Tor выключен",16);c.addView(status);
        start=button(c,"Запустить Tor",this::startTor);stop=button(c,"Остановить Tor",()->stopTor("Tor выключен"));
        check=button(c,"Проверить соединение Tor",()->request(false));
        address=new EditText(this);address.setSingleLine(true);address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);address.setTextColor(TEXT);address.setHintTextColor(0xffa5b1ce);address.setHint("http://… .onion");String supplied=getIntent().getStringExtra("onion");if(supplied!=null)address.setText(supplied);c.addView(address);
        read=button(c,"Открыть текст .onion через Tor",()->request(true));
        result=text("",14);result.setTextIsSelectable(true);c.addView(result);
        c.addView(text("Текстовый режим: без JavaScript, картинок, cookies, автоматических переходов и внешних ресурсов. Адрес не сохраняется в истории AetherBolt. Для полноценного просмотра используйте официальный Tor Browser.",13));
        button(c,"Открыть в официальном Tor Browser",this::official);
        button(c,"Закрыть Tor Engine",this::finish);
        setContentView(scroll);update();
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String t,int size){TextView v=new TextView(this);v.setText(t);v.setTextColor(TEXT);v.setTextSize(size);v.setPadding(0,dp(10),0,dp(10));return v;}
    private Button button(LinearLayout c,String title,Runnable action){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setOnClickListener(v->action.run());c.addView(b,new LinearLayout.LayoutParams(-1,dp(54)));return b;}
    private void update(){if(start==null)return;start.setEnabled(!bound);stop.setEnabled(bound);check.setEnabled(service!=null&&!busy);read.setEnabled(service!=null&&!busy);}
    private void startTor(){
        if(bound)return;
        try{
            bound=bindService(new Intent(this,TorService.class),connection,BIND_AUTO_CREATE);
            status.setText(bound?"Запуск клиента Tor…":"Не удалось запустить Tor");
        }catch(RuntimeException|LinkageError e){bound=false;status.setText("Клиент Tor недоступен");}
        update();
    }
    private void stopTor(String message){
        generation++;Socket socket=activeSocket.getAndSet(null);if(socket!=null)try{socket.close();}catch(IOException ignored){}
        busy=false;service=null;
        if(bound){bound=false;try{unbindService(connection);}catch(IllegalArgumentException ignored){}}
        if(status!=null){status.setText(message);result.setText("");}update();
    }
    private void request(boolean onion){
        if(service==null||busy)return;
        final URI uri;
        if(onion){uri=TorHttp.onion(address.getText().toString());if(uri==null){address.setError("Нужен корректный v3 .onion URL с http:// или https://");return;}}
        else uri=URI.create("https://check.torproject.org/api/ip");
        final TorService tor=service;final int ticket=++generation;
        busy=true;status.setText("Запрос только через Tor…");result.setText("");update();
        new Thread(()->{
            String message,body="";
            try{
                int port=-1;long deadline=SystemClock.elapsedRealtime()+60000;
                while(ticket==generation&&SystemClock.elapsedRealtime()<deadline){
                    port=tor.getSocksPort();if(port>0)break;Thread.sleep(500);
                }
                if(ticket!=generation)return;
                TorHttp.Reply reply=TorHttp.fetch(port,uri,socket->{
                    synchronized(activeSocket){if(ticket!=generation)throw new IOException("Отменено");activeSocket.set(socket);if(ticket!=generation){socket.close();throw new IOException("Отменено");}}
                });
                if(onion){
                    message="HTTP "+reply.status+" • соединение через локальный SOCKS Tor";
                    body=reply.type.startsWith("text/html")?Html.fromHtml(reply.body,Html.FROM_HTML_MODE_LEGACY).toString():reply.body;
                }else{
                    boolean ok=reply.status==200&&new org.json.JSONObject(reply.body).optBoolean("IsTor",false);
                    message=ok?"Tor подтверждён сервером Tor Project":"Сервер не подтвердил Tor";body="Проверка касается только этого запроса, не обычных вкладок.";
                }
            }catch(Exception e){message="Не удалось выполнить запрос через Tor. Проверьте соединение и повторите.";body="Прямое соединение не используется.";}
            final String msg=message,output=body;
            ui.post(()->{if(ticket!=generation||isDestroyed())return;activeSocket.set(null);busy=false;status.setText(msg);result.setText(output);update();});
        },"aether-tor-request").start();
    }
    private void official(){
        String raw=address.getText().toString().trim();
        URI onion=raw.isEmpty()?null:TorHttp.onion(raw);
        if(!raw.isEmpty()&&onion==null){address.setError("Введите v3 .onion URL или оставьте поле пустым");return;}
        Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(onion==null?"https://check.torproject.org/":onion.toString())).setPackage("org.torproject.torbrowser");
        try{startActivity(i);}catch(ActivityNotFoundException e){
            new android.app.AlertDialog.Builder(this).setTitle("Tor Browser не установлен")
                .setMessage("Установите официальный Tor Browser с сайта torproject.org/download. Адрес .onion не передан другому браузеру.")
                .setPositiveButton("OK",null).show();
        }
    }
    @Override protected void onStop(){stopTor("Tor выключен");super.onStop();}
    @Override protected void onDestroy(){ui.removeCallbacksAndMessages(null);super.onDestroy();}
}
