package com.aetherbolt.browser;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.WindowManager;
import android.widget.Toast;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.json.*;
import org.mozilla.geckoview.*;
import org.mozilla.geckoview.GeckoSession.PromptDelegate.*;

/** Gecko native login prompts backed by the authenticated existing vault. */
final class BrowserPasswords implements Autocomplete.StorageDelegate {
    static final int AUTH=983;
    private final Activity ui;
    private final VaultStore store;
    private final Supplier<String> currentOrigin;
    private final Predicate<GeckoSession> currentSession;
    private final BooleanSupplier enabled;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private long until=0;
    private String unlockedOrigin="";
    private int generation;
    private boolean closed,credentialPending,busy;
    private Runnable authSuccess,authCancel;
    private CancellationSignal signal;
    private AlertDialog dialog;
    BrowserPasswords(Activity a,Supplier<String> origin,Predicate<GeckoSession> session,BooleanSupplier enabled){
        ui=a;store=new VaultStore(a);currentOrigin=origin;currentSession=session;this.enabled=enabled;
    }
    static String origin(String raw){
        if(raw==null||raw.length()>8192)return "";
        try{
            URI u=new URI(raw);String h=u.getHost();
            if(!"https".equalsIgnoreCase(u.getScheme())||h==null||u.getRawUserInfo()!=null||u.getPort()>65535||u.getPort()==0)return "";
            int port=u.getPort();return "https://"+h.toLowerCase(Locale.ROOT)+(port==-1||port==443?"":":"+port);
        }catch(Exception e){return "";}
    }
    private boolean allowed(){return !closed&&!ui.isFinishing()&&!ui.isDestroyed()&&enabled.getAsBoolean();}
    boolean unlocked(){return allowed()&&SystemClock.elapsedRealtime()<until&&!unlockedOrigin.isEmpty()&&unlockedOrigin.equals(currentOrigin.get());}
    private void toast(String m){if(!closed&&!ui.isDestroyed())Toast.makeText(ui,m,Toast.LENGTH_SHORT).show();}
    private boolean valid(Autocomplete.LoginEntry e){
        String o=origin(e.origin);
        return !o.isEmpty()&&e.httpRealm==null&&o.equals(origin(e.formActionOrigin))&&e.username.length()<=512&&e.password.length()>0&&e.password.length()<=4096;
    }
    private boolean samePage(GeckoSession session,Autocomplete.LoginEntry e){return allowed()&&currentSession.test(session)&&valid(e)&&origin(e.origin).equals(currentOrigin.get());}
    private void secure(AlertDialog d){dialog=d;d.show();if(d.getWindow()!=null)d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);}
    private void authenticate(Runnable success,Runnable cancel){
        if(!allowed()||busy||authSuccess!=null){cancel.run();return;}
        KeyguardManager km=(KeyguardManager)ui.getSystemService(Context.KEYGUARD_SERVICE);
        if(km==null||!km.isDeviceSecure()){toast("Настройте PIN или пароль Android");cancel.run();return;}
        busy=true;int token=++generation;authSuccess=success;authCancel=cancel;
        worker.execute(()->{boolean ok;try{store.prepare();ok=true;}catch(Exception e){ok=false;}final boolean ready=ok;
            handler.post(()->{if(closed||token!=generation)return;busy=false;if(!ready){finishAuth(false);toast("Ключ хранилища недоступен");return;}
                try{
                    if(Build.VERSION.SDK_INT>=30){
                        signal=new CancellationSignal();
                        new android.hardware.biometrics.BiometricPrompt.Builder(ui).setTitle("Password Vault").setSubtitle("Подтвердите доступ к паролям").setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG|android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL).build().authenticate(signal,ui.getMainExecutor(),new android.hardware.biometrics.BiometricPrompt.AuthenticationCallback(){
                            @Override public void onAuthenticationSucceeded(android.hardware.biometrics.BiometricPrompt.AuthenticationResult r){if(token==generation)finishAuth(true);}
                            @Override public void onAuthenticationError(int code,CharSequence text){if(token==generation)finishAuth(false);}
                        });
                    }else{
                        Intent i=km.createConfirmDeviceCredentialIntent("Password Vault","Подтвердите код устройства");
                        if(i==null){finishAuth(false);return;}credentialPending=true;ui.startActivityForResult(i,AUTH);
                    }
                }catch(RuntimeException e){finishAuth(false);}
            });
        });
    }
    private void finishAuth(boolean ok){
        Runnable yes=authSuccess,no=authCancel;authSuccess=null;authCancel=null;signal=null;credentialPending=false;busy=false;
        if(ok&&allowed()){unlockedOrigin=currentOrigin.get();until=SystemClock.elapsedRealtime()+45000;if(yes!=null)yes.run();}else{until=0;if(no!=null)no.run();}
    }
    boolean onActivityResult(int request,int result){
        if(request!=AUTH)return false;if(credentialPending)finishAuth(result==Activity.RESULT_OK);return true;
    }
    void unlock(Runnable done){if(currentOrigin.get().isEmpty()){toast("Сначала откройте HTTPS-сайт с формой входа");return;}authenticate(()->{toast("Автозаполнение разблокировано на 45 секунд");done.run();},()->toast("Доступ отменён"));}
    void lock(){
        until=0;unlockedOrigin="";generation++;busy=false;credentialPending=false;
        Runnable no=authCancel;authCancel=null;authSuccess=null;
        if(signal!=null){CancellationSignal old=signal;signal=null;old.cancel();}
        if(dialog!=null){AlertDialog d=dialog;dialog=null;d.dismiss();}
        if(no!=null)no.run();
    }
    void stopped(){until=0;if(!credentialPending)lock();}
    void destroy(){lock();closed=true;worker.shutdownNow();handler.removeCallbacksAndMessages(null);}
    @Override public GeckoResult<Autocomplete.LoginEntry[]> onLoginFetch(String domain){
        String o=currentOrigin.get();
        if(!unlocked()||o.isEmpty()||domain==null)return GeckoResult.fromValue(new Autocomplete.LoginEntry[0]);
        String host=URI.create(o).getHost();if(!domain.equalsIgnoreCase(host))return GeckoResult.fromValue(new Autocomplete.LoginEntry[0]);
        GeckoResult<Autocomplete.LoginEntry[]> result=new GeckoResult<>();int token=generation;
        worker.execute(()->{
            ArrayList<Autocomplete.LoginEntry> list=new ArrayList<>();
            try{JSONArray rows=store.read();for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r==null||!o.equals(origin(r.optString("site"))))continue;String user=r.optString("user"),pass=r.optString("pass");
                if(pass.isEmpty()||pass.length()>4096||user.length()>512)continue;
                list.add(new Autocomplete.LoginEntry.Builder().guid(UUID.nameUUIDFromBytes((o+"\n"+user).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString()).origin(o).formActionOrigin(o).username(user).password(pass).build());
            }}catch(Exception ignored){}
            handler.post(()->result.complete(!closed&&token==generation&&unlocked()&&o.equals(currentOrigin.get())?list.toArray(new Autocomplete.LoginEntry[0]):new Autocomplete.LoginEntry[0]));
        });return result;
    }
    @Override public GeckoResult<Autocomplete.LoginEntry[]> onLoginFetch(){return GeckoResult.fromValue(new Autocomplete.LoginEntry[0]);}
    // Writes happen only after our secure confirmation and device authentication.
    @Override public void onLoginSave(Autocomplete.LoginEntry login){}
    GeckoResult<PromptResponse> save(GeckoSession session,AutocompleteRequest<Autocomplete.LoginSaveOption> request){
        if(!allowed()||dialog!=null||authSuccess!=null||busy||request.options.length==0)return GeckoResult.fromValue(request.dismiss());
        Autocomplete.LoginSaveOption option=request.options[0];Autocomplete.LoginEntry entry=option.value;
        if(!samePage(session,entry))return GeckoResult.fromValue(request.dismiss());
        GeckoResult<PromptResponse> result=new GeckoResult<>();boolean[] accepted={false};
        Runnable deny=()->{if(!request.isComplete())result.complete(request.dismiss());};
        AlertDialog d=new AlertDialog.Builder(ui).setTitle("Сохранить логин и пароль?").setMessage(origin(entry.origin)+"\n"+entry.username+"\nЗапись для этого сайта и логина будет добавлена или обновлена в Password Vault.").setNegativeButton("Не сейчас",null).setPositiveButton("Сохранить",(x,w)->{
            accepted[0]=true;authenticate(()->{
                if(!samePage(session,entry)||request.isComplete()){deny.run();return;}int token=generation;busy=true;
                worker.execute(()->{boolean ok=false;try{JSONArray rows=store.read();String o=origin(entry.origin);int index=-1;
                    for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r!=null&&o.equals(origin(r.optString("site")))&&entry.username.equals(r.optString("user"))){index=i;break;}}
                    if(index<0&&rows.length()>=200)throw new java.io.IOException("limit");
                    JSONObject row=new JSONObject().put("site",o).put("user",entry.username).put("pass",entry.password);
                    if(index<0)rows.put(row);else rows.put(index,row);store.write(rows);ok=true;
                }catch(Exception ignored){}final boolean done=ok;
                handler.post(()->{if(token==generation)busy=false;if(!request.isComplete()){if(done&&samePage(session,entry)&&token==generation)result.complete(request.confirm(option));else deny.run();}toast(done?"Логин сохранён в Password Vault":"Не удалось сохранить пароль");});});
            },deny);
        }).create();
        d.setOnDismissListener(x->{if(dialog==d)dialog=null;if(!accepted[0])deny.run();});secure(d);return result;
    }
    GeckoResult<PromptResponse> select(GeckoSession session,AutocompleteRequest<Autocomplete.LoginSelectOption> request){
        if(!unlocked()||dialog!=null)return GeckoResult.fromValue(request.dismiss());
        ArrayList<Autocomplete.LoginSelectOption> choices=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();
        for(Autocomplete.LoginSelectOption option:request.options)if(samePage(session,option.value)){choices.add(option);labels.add(option.value.username.isEmpty()?"Без логина":option.value.username);}
        if(choices.isEmpty())return GeckoResult.fromValue(request.dismiss());
        GeckoResult<PromptResponse> result=new GeckoResult<>();
        AlertDialog d=new AlertDialog.Builder(ui).setTitle("Выбрать аккаунт • "+currentOrigin.get()).setItems(labels.toArray(new String[0]),(x,w)->{if(!request.isComplete()){if(unlocked()&&samePage(session,choices.get(w).value))result.complete(request.confirm(choices.get(w)));else result.complete(request.dismiss());}}).setNegativeButton("Отмена",null).create();
        d.setOnDismissListener(x->{if(dialog==d)dialog=null;if(!request.isComplete())result.complete(request.dismiss());});secure(d);return result;
    }
}
