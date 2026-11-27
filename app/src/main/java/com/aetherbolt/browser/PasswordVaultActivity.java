package com.aetherbolt.browser;

import android.app.*;
import android.content.*;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.json.*;
import java.util.concurrent.*;

public final class PasswordVaultActivity extends Activity {
    private static final int AUTH=961, BG=0xff0b1020,TEXT=0xffedf2ff;
    private static final long SESSION_MS=45000;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private VaultStore store;
    private LinearLayout root,body;
    private TextView status;
    private JSONArray entries;
    private AlertDialog dialog;
    private EditText[] fields;
    private CancellationSignal signal;
    private boolean authenticating,visible,busy,unlocked,credentialPending;
    private int generation;
    private long expiry;
    private final Runnable timeout=()->lock("Хранилище заблокировано по таймеру");
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextColor(TEXT);t.setTextSize(size);t.setPadding(dp(8),dp(8),dp(8),dp(8));t.setSaveEnabled(false);return t;}
    private void button(LinearLayout p,String name,Runnable r){Button b=new Button(this);b.setText(name);b.setAllCaps(false);b.setSaveEnabled(false);b.setOnClickListener(v->r.run());p.addView(b,new LinearLayout.LayoutParams(-1,dp(50)));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if(!ModuleCatalog.guard(this,"vault")){finish();return;}
        store=new VaultStore(this);root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(12),dp(8),dp(12),dp(8));root.setBackgroundColor(BG);root.setSaveFromParentEnabled(false);root.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        root.addView(text("Password Vault",24));status=text("Хранилище заблокировано",14);root.addView(status);
        ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(1);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);lockedView();
    }
    private void lockedView(){
        if(body==null)return;body.removeAllViews();
        body.addView(text("Локальное хранилище AES-256-GCM. Для доступа требуется защищённая блокировка Android. Браузер может сохранять логины HTTPS и автозаполнять их после отдельной разблокировки. Нет синхронизации и восстановления ключа. Если ключ будет утрачен, записи восстановить нельзя.",14));
        button(body,"Разблокировать",this::beginAuth);button(body,"Закрыть",this::finish);
    }
    private void lock(String message){
        generation++;unlocked=false;entries=null;expiry=0;busy=false;authenticating=false;credentialPending=false;
        ui.removeCallbacks(timeout);if(signal!=null){signal.cancel();signal=null;}
        if(fields!=null){for(EditText f:fields)f.setText("");fields=null;}
        if(dialog!=null){dialog.dismiss();dialog=null;}
        if(status!=null)status.setText(message);lockedView();
    }
    private boolean valid(){
        if(unlocked&&visible&&SystemClock.elapsedRealtime()<expiry)return true;
        if(unlocked)lock("Срок доступа истёк");return false;
    }
    private void beginAuth(){
        if(authenticating||busy||unlocked||!visible)return;
        KeyguardManager km=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        if(km==null||!km.isDeviceSecure()){status.setText("Настройте PIN, пароль или графический ключ Android");return;}
        authenticating=true;int token=++generation;status.setText("Подготовка Android Keystore…");
        worker.execute(()->{boolean ok;try{store.prepare();ok=true;}catch(Exception e){ok=false;}final boolean ready=ok;ui.post(()->{
            if(isDestroyed()||token!=generation||!visible)return;
            if(!ready){lock("Ключ недоступен. Данные не удалены.");return;}
            prompt(token,km);
        });});
    }
    private void prompt(int token,KeyguardManager km){
        try{
            if(Build.VERSION.SDK_INT>=30){
                signal=new CancellationSignal();
                android.hardware.biometrics.BiometricPrompt p=new android.hardware.biometrics.BiometricPrompt.Builder(this)
                    .setTitle("Разблокировать Password Vault").setSubtitle("Биометрия или код устройства")
                    .setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG|android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL).build();
                p.authenticate(signal,getMainExecutor(),new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback(){
                    @Override public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult r){if(token==generation){signal=null;authenticated(token);}}
                    @Override public void onAuthenticationError(int code,CharSequence msg){if(token==generation)lock("Аутентификация отменена или недоступна");}
                });
            }else{
                Intent i=km.createConfirmDeviceCredentialIntent("Password Vault","Подтвердите код устройства");
                if(i==null){lock("Аутентификация недоступна");return;}
                credentialPending=true;startActivityForResult(i,AUTH);
            }
        }catch(RuntimeException e){lock("Аутентификация недоступна");}
    }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);if(req!=AUTH||!credentialPending)return;
        credentialPending=false;if(result!=RESULT_OK){lock("Аутентификация отменена");return;}
        final int token=generation;ui.post(()->authenticated(token));
    }
    private void authenticated(int token){
        if(token!=generation||!authenticating||isDestroyed())return;
        authenticating=false;busy=true;status.setText("Расшифровка…");
        long deadline=SystemClock.elapsedRealtime()+SESSION_MS;
        worker.execute(()->{JSONArray data=null;try{data=store.read();}catch(Exception ignored){}final JSONArray result=data;ui.post(()->{
            if(token!=generation||isDestroyed())return;busy=false;
            if(result==null||!visible||SystemClock.elapsedRealtime()>=deadline){lock("Не удалось открыть хранилище. Данные не удалены.");return;}
            entries=result;unlocked=true;expiry=deadline;ui.postDelayed(timeout,Math.max(1,expiry-SystemClock.elapsedRealtime()));showEntries();
        });});
    }
    private void showEntries(){
        if(!valid())return;body.removeAllViews();status.setText("Разблокировано • до 45 секунд");
        button(body,"Заблокировать",()->lock("Хранилище заблокировано"));
        button(body,"+ Добавить запись",()->edit(-1));
        body.addView(text("Записей: "+entries.length(),14));
        for(int i=0;i<entries.length();i++){JSONObject e=entries.optJSONObject(i);if(e==null)continue;final int index=i;
            button(body,e.optString("site","Запись")+" • "+e.optString("user"),()->edit(index));
        }
    }
    private EditText field(String hint,String value,boolean secret){
        EditText e=new EditText(this);e.setHint(hint);e.setSaveEnabled(false);e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);e.setSingleLine(true);e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(secret?4096:512)});
        e.setInputType(InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));e.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);e.setText(value);return e;
    }
    private void edit(int index){
        if(!valid()||busy)return;
        JSONObject old=index<0?new JSONObject():entries.optJSONObject(index);if(old==null)return;
        LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(dp(16),dp(8),dp(16),dp(8));form.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        EditText site=field("Сайт / название",old.optString("site"),false),user=field("Логин",old.optString("user"),false),pass=field("Пароль",old.optString("pass"),true);
        fields=new EditText[]{site,user,pass};form.addView(site);form.addView(user);form.addView(pass);
        CheckBox reveal=new CheckBox(this);reveal.setText("Показать пароль");reveal.setOnCheckedChangeListener((v,on)->{if(valid()){pass.setTransformationMethod(on?null:android.text.method.PasswordTransformationMethod.getInstance());pass.setSelection(pass.length());}});form.addView(reveal);
        button(form,"Сгенерировать пароль • 24 символа",()->{if(valid())pass.setText(VaultPasswords.generate(24));});
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(index<0?"Новая запись":"Изменить запись").setView(form).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null);
        if(index>=0)builder.setNeutralButton("Удалить",null);
        AlertDialog d=builder.create();dialog=d;d.setOnDismissListener(v->{if(fields!=null){for(EditText f:fields)f.setText("");fields=null;}if(dialog==d)dialog=null;});
        d.show();if(d.getWindow()!=null)d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        d.getButton(-1).setOnClickListener(v->{
            if(!valid()||busy)return;
            String title=site.getText().toString().trim();if(title.isEmpty()||pass.length()==0){site.setError("Нужны название и пароль");return;}
            if(index<0&&entries.length()>=200){site.setError("Лимит 200 записей");return;}
            try{JSONArray next=new JSONArray(entries.toString());JSONObject row=new JSONObject().put("site",title).put("user",user.getText().toString()).put("pass",pass.getText().toString());if(index<0)next.put(row);else next.put(index,row);d.dismiss();save(next);}catch(JSONException e){lock("Ошибка записи; данные не удалены");}
        });
        if(index>=0)d.getButton(-3).setOnClickListener(v->{
            if(!valid()||busy)return;d.dismiss();AlertDialog confirm=new AlertDialog.Builder(this).setTitle("Удалить запись?").setMessage("Восстановление записи не предусмотрено.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(x,w)->{if(!valid()||busy)return;try{JSONArray next=new JSONArray(entries.toString());next.remove(index);save(next);}catch(JSONException e){lock("Ошибка записи");}}).create();
            dialog=confirm;confirm.setOnDismissListener(vv->{if(dialog==confirm)dialog=null;});confirm.show();if(confirm.getWindow()!=null)confirm.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        });
    }
    private void save(JSONArray next){
        if(!valid()||busy)return;busy=true;int token=generation;status.setText("Сохранение…");
        worker.execute(()->{boolean ok;try{store.write(next);ok=true;}catch(Exception e){ok=false;}final boolean done=ok;ui.post(()->{
            if(token!=generation||isDestroyed())return;busy=false;
            if(!done||!valid()){lock(done?"Хранилище заблокировано":"Не удалось сохранить. Разблокируйте и проверьте записи.");return;}
            entries=next;showEntries();
        });});
    }
    @Override protected void onStart(){super.onStart();visible=true;}
    @Override protected void onStop(){visible=false;if(!credentialPending)lock("Хранилище заблокировано");super.onStop();}
    @Override protected void onDestroy(){lock("Хранилище заблокировано");ui.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
}
