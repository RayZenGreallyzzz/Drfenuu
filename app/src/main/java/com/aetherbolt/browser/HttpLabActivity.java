package com.aetherbolt.browser;
import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public final class HttpLabActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private EditText url,body,contentType;
    private Spinner method;
    private TextView result;
    private volatile HttpURLConnection connection;
    private boolean busy;
    private int generation;
    private TextView text(String s){TextView v=new TextView(this);v.setText(s);v.setTextColor(0xffedf2ff);v.setPadding(12,12,12,12);v.setTextIsSelectable(true);return v;}
    private EditText field(String hint,String initial,int limit){EditText e=new EditText(this);e.setHint(hint);e.setText(initial);e.setTextColor(0xffedf2ff);e.setSaveEnabled(false);e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(limit)});return e;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(16,16,16,16);root.setBackgroundColor(0xff0b1020);scroll.addView(root);setContentView(scroll);
        root.addView(text("HTTP Lab • редактор запросов"));root.addView(text("Только локальный тестовый сервер на 127.0.0.1 или ::1. Отдельное соединение без cookies GeckoView, редиректов и прокси. Запускайте запросы вручную."));
        url=field("URL","http://127.0.0.1:8080/",2048);root.addView(url);
        method=new Spinner(this);method.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"GET","POST","HEAD"}));root.addView(method);
        contentType=field("Content-Type","application/json",120);root.addView(contentType);
        body=field("Тело POST","",16000);body.setMinLines(3);root.addView(body);
        Button send=new Button(this);send.setText("Отправить локальный запрос");send.setOnClickListener(v->confirm());root.addView(send);
        Button cancel=new Button(this);cancel.setText("Отменить");cancel.setOnClickListener(v->cancel());root.addView(cancel);
        result=text("");root.addView(result);
    }
    private void confirm(){
        if(busy)return;
        try{
            URI u=new URI(url.getText().toString().trim());String h=u.getHost();
            if(!("127.0.0.1".equals(h)||"[::1]".equals(h))||!("http".equals(u.getScheme())||"https".equals(u.getScheme()))||u.getRawUserInfo()!=null||u.getRawFragment()!=null||u.getPort()==0||u.getPort()>65535)throw new IllegalArgumentException();
            String verb=method.getSelectedItem().toString(),type=contentType.getText().toString(),payload=body.getText().toString();
            if(!type.matches("[a-zA-Z0-9.+/-]+(?:; charset=[a-zA-Z0-9_-]+)?"))throw new IllegalArgumentException();
            new AlertDialog.Builder(this).setTitle("Выполнить локальный запрос?").setMessage(verb+" "+u).setNegativeButton("Отмена",null).setPositiveButton("Отправить",(d,w)->send(u,verb,type,payload)).show();
        }catch(Exception e){result.setText("Допускаются только локальные HTTP(S) URL и корректный Content-Type");}
    }
    private void send(URI u,String verb,String type,String payload){
        if(busy)return;busy=true;int id=++generation;result.setText("Запрос…");worker.execute(()->{
            String answer;HttpURLConnection c=null;
            try{
                c=(HttpURLConnection)u.toURL().openConnection(Proxy.NO_PROXY);connection=c;c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setConnectTimeout(5000);c.setReadTimeout(8000);c.setRequestMethod(verb);c.setRequestProperty("Accept","*/*");
                long start=SystemClock.elapsedRealtime();
                if("POST".equals(verb)){byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);c.setDoOutput(true);c.setRequestProperty("Content-Type",type);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}}
                int code=c.getResponseCode();StringBuilder out=new StringBuilder("HTTP ").append(code).append(" • ").append(SystemClock.elapsedRealtime()-start).append(" мс\n");
                for(String name:new String[]{"Content-Type","Content-Length","Cache-Control","Date","Server"}){String value=c.getHeaderField(name);if(value!=null)out.append(name).append(": ").append(value).append('\n');}
                InputStream raw=code>=400?c.getErrorStream():c.getInputStream();
                if(raw!=null)try(InputStream in=raw;ByteArrayOutputStream data=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while(data.size()<65536&&(n=in.read(b,0,Math.min(b.length,65536-data.size())))!=-1)data.write(b,0,n);out.append("\n").append(new String(data.toByteArray(),StandardCharsets.UTF_8));if(data.size()==65536)out.append("\n[Лимит ответа 64 КиБ]");}
                answer=out.toString();
            }catch(Exception e){answer="Не удалось подключиться к локальному серверу";}finally{connection=null;if(c!=null)c.disconnect();}
            final String value=answer;runOnUiThread(()->{if(!isDestroyed()&&id==generation){busy=false;result.setText(value);}});
        });
    }
    private void cancel(){generation++;busy=false;HttpURLConnection c=connection;if(c!=null)new Thread(c::disconnect).start();if(result!=null)result.setText("Отменено");}
    @Override protected void onStop(){cancel();super.onStop();}
    @Override protected void onDestroy(){cancel();worker.shutdownNow();super.onDestroy();}
}
