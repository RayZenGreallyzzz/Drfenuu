package com.aetherbolt.browser;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.json.*;
import java.net.URI;
import java.util.concurrent.*;

public final class AiCenterActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private JSONArray history=new JSONArray();
    private EditText endpoint,model,token,input;
    private TextView output,status;
    private Spinner mode;
    private AiClient active;
    private boolean busy,visible;
    private int generation;
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(0xffedf2ff);t.setSaveEnabled(false);t.setPadding(dp(8),dp(8),dp(8),dp(8));return t;}
    private EditText field(String hint,int max,boolean secret){
        EditText e=new EditText(this);e.setHint(hint);e.setSaveEnabled(false);e.setTextColor(0xffedf2ff);e.setHintTextColor(0xff9caac5);e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);e.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(max)});
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|(secret?android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD:android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));e.setSingleLine(true);return e;
    }
    private void button(LinearLayout p,String title,Runnable r){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setOnClickListener(v->r.run());p.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if(!ModuleCatalog.guard(this,"ai")){finish();return;}
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(12),dp(8),dp(12),dp(8));root.setBackgroundColor(0xff0b1020);scroll.addView(root);setContentView(scroll);
        root.addView(text("AI Center",24));
        root.addView(text("Подключите собственный HTTPS-сервер OpenAI-compatible или Ollama через HTTPS/локальный loopback. Не вводите ключ OpenAI: он должен быть на сервере. Токен доступа к вашему серверу и чат не сохраняются. API может быть платным.",14));
        endpoint=field("https://ваш-сервер/v1/chat/completions",2048,false);model=field("ID модели на сервере",128,false);token=field("Токен вашего сервера (не OpenAI key)",512,true);
        root.addView(endpoint);root.addView(model);root.addView(token);
        mode=new Spinner(this);mode.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Чат","Краткое содержание","Перевести на русский","Перевести на украинский","Объяснить код"}));root.addView(mode);
        input=field("Введите текст или вопрос",24000,false);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);input.setSingleLine(false);input.setMinLines(4);input.setMaxLines(9);root.addView(input);
        String draft=getIntent().getStringExtra("draft");getIntent().removeExtra("draft");if(draft!=null){input.setText(draft);mode.setSelection(1);}
        LinearLayout actions=new LinearLayout(this);button(actions,"Отправить",this::confirm);button(actions,"Отмена",this::cancel);button(actions,"Новый чат",this::clearChat);root.addView(actions);
        status=text("Ожидает настройки",14);root.addView(status);output=text("",16);output.setTextIsSelectable(true);root.addView(output);
        root.addView(text("Текст и история этого чата отправляются выбранному серверу после подтверждения. Нативный API не использует прокси, DNS-over-HTTPS или Tor браузера. Системный VPN зависит от настройки устройства.",13));
    }
    private void confirm(){
        if(busy||!visible)return;
        try{
            URI uri=AiClient.endpoint(endpoint.getText().toString());String name=model.getText().toString().trim(),secret=token.getText().toString().trim(),message=input.getText().toString();
            if(!name.matches("[a-zA-Z0-9._:/-]{1,128}")){model.setError("Введите ID модели");return;}
            if(secret.startsWith("sk-")||secret.contains("\n")||secret.contains("\r")){token.setError("Не вводите ключ OpenAI");return;}
            if(!secret.isEmpty()&&!"https".equalsIgnoreCase(uri.getScheme())){token.setError("Токен только по HTTPS");return;}
            if(message.trim().isEmpty()){input.setError("Введите текст");return;}
            String[] prompts={"","Сделай краткое содержание следующего текста:\n","Переведи следующий текст на русский:\n","Переведи следующий текст на украинский:\n","Объясни следующий код:\n"};
            String question=prompts[mode.getSelectedItemPosition()]+message;
            JSONArray request=new JSONArray(history.toString());request.put(new JSONObject().put("role","user").put("content",question));
            if(request.length()>13||request.toString().length()>AiClient.MAX_INPUT){status.setText("Лимит контекста. Начните новый чат или сократите ввод.");return;}
            new AlertDialog.Builder(this).setTitle("Отправить запрос?").setMessage("Получатель: "+uri.getHost()+"\nМодель: "+name+"\nБудут отправлены введённый текст и история этого чата ("+request.length()+" сообщений). Возможны расходы API. Сетевые настройки GeckoView не применяются.").setNegativeButton("Отмена",null).setPositiveButton("Отправить",(d,w)->send(uri,name,secret,request)).show();
        }catch(Exception e){status.setText("Ошибка: "+e.getMessage());}
    }
    private void send(URI uri,String name,String secret,JSONArray request){
        if(busy||!visible)return;busy=true;int id=++generation;AiClient client=new AiClient();active=client;status.setText("Запрос выполняется…");
        worker.execute(()->{String answer=null,error=null;try{answer=client.request(uri,name,secret,request);}catch(Exception e){error=e instanceof java.net.SocketTimeoutException?"Время ожидания истекло":e instanceof java.io.IOException&&e.getMessage()!=null&&e.getMessage().startsWith("Сервер вернул HTTP")?e.getMessage():"Не удалось получить ответ. Проверьте сервер, модель и соединение.";}
            final String result=answer,err=error;runOnUiThread(()->{
                if(isDestroyed()||generation!=id)return;busy=false;active=null;
                if(err!=null){status.setText(err);return;}
                try{request.put(new JSONObject().put("role","assistant").put("content",result));history=request;input.setText("");render();status.setText("Ответ получен");}catch(Exception e){status.setText("Ошибка обработки ответа");}
            });
        });
    }
    private void render(){StringBuilder out=new StringBuilder();for(int i=0;i<history.length();i++){JSONObject j=history.optJSONObject(i);if(j!=null)out.append("user".equals(j.optString("role"))?"Вы:\n":"AI:\n").append(j.optString("content")).append("\n\n");}output.setText(out);}
    private void cancel(){generation++;AiClient c=active;active=null;busy=false;if(c!=null)new Thread(c::cancel,"aether-ai-cancel").start();if(status!=null)status.setText("Отменено; сервер мог уже обработать запрос");}
    private void clearChat(){cancel();history=new JSONArray();input.setText("");output.setText("");status.setText("Новый чат");}
    @Override protected void onStart(){super.onStart();visible=true;}
    @Override protected void onStop(){visible=false;if(busy)cancel();if(token!=null)token.setText("");super.onStop();}
    @Override protected void onDestroy(){cancel();history=new JSONArray();if(input!=null)input.setText("");if(output!=null)output.setText("");worker.shutdownNow();super.onDestroy();}
}
