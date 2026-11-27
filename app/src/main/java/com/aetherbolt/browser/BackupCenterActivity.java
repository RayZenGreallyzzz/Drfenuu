package com.aetherbolt.browser;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class BackupCenterActivity extends Activity {
    private static final int EXPORT=981,IMPORT=982;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout root;private TextView status;private CheckBox tabs,offline;
    private char[] password;private boolean busy,waiting,restored;
    private JSONObject pending;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(0xffedf2ff);t.setPadding(dp(8),dp(8),dp(8),dp(8));return t;}
    private void button(String title,Runnable r){Button b=new Button(this);b.setAllCaps(false);b.setText(title);b.setOnClickListener(v->{if(!busy&&!waiting)r.run();});root.addView(b,new LinearLayout.LayoutParams(-1,dp(52)));}
    private CheckBox check(String title,boolean value){CheckBox c=new CheckBox(this);c.setText(title);c.setTextColor(0xffedf2ff);c.setChecked(value);return c;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(16),dp(8),dp(16),dp(8));root.setBackgroundColor(0xff0b1020);
        root.addView(text("Backup Center",24));root.addView(text("AES-256-GCM • PBKDF2-SHA256 • 600 000 итераций. Отдельный пароль от 12 символов. Без него восстановление невозможно.",15));
        root.addView(text("Экспорт: параметры интерфейса, производительности, модулей и закладки. Не включает пароли, cookies, историю, VPN, DNS, прокси, скрипты, расширения, ключи подписи или скачанные файлы.",14));
        tabs=check("Включить адреса открытых вкладок",false);offline=check("Включить офлайн-библиотеку",false);root.addView(tabs);root.addView(offline);
        button("Создать зашифрованную копию",()->askPassword(true,null));button("Импортировать копию",this::pickImport);button("Назад",this::finish);
        status=text("Выберите действие",14);root.addView(status);ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
    }
    private EditText secret(String hint){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);e.setSaveEnabled(false);e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);e.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(256)});return e;}
    private void askPassword(boolean exporting,Uri source){
        LinearLayout form=new LinearLayout(this);form.setOrientation(1);EditText one=secret("Пароль (12–256 символов)"),two=secret("Повторите пароль");form.addView(one);if(exporting)form.addView(two);
        AlertDialog d=new AlertDialog.Builder(this).setTitle(exporting?"Пароль резервной копии":"Расшифровать копию").setView(form).setNegativeButton("Отмена",null).setPositiveButton("Продолжить",null).create();
        d.setOnDismissListener(v->{one.setText("");two.setText("");});d.show();if(d.getWindow()!=null)d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        d.getButton(-1).setOnClickListener(v->{if(one.length()<12||(exporting&&!android.text.TextUtils.equals(one.getText(),two.getText()))){one.setError("Проверьте длину и совпадение");return;}
            char[] pw=new char[one.length()];one.getText().getChars(0,one.length(),pw,0);d.dismiss();
            if(exporting){clearPassword();password=pw;waiting=true;try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/octet-stream").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"AetherBolt.aebak"),EXPORT);}catch(Exception ex){waiting=false;clearPassword();status.setText("Не удалось выбрать файл");}}
            else decrypt(source,pw);
        });
    }
    private void clearPassword(){if(password!=null){Arrays.fill(password,'\0');password=null;}}
    private void pickImport(){waiting=true;try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),IMPORT);}catch(Exception e){waiting=false;status.setText("Не удалось выбрать файл");}}
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);waiting=false;if(result!=RESULT_OK||data==null||data.getData()==null){clearPassword();return;}
        if(req==IMPORT)askPassword(false,data.getData());
        else if(req==EXPORT&&password!=null){char[] pw=password;password=null;Uri target=data.getData();boolean t=tabs.isChecked(),o=offline.isChecked();
            run(()->{byte[] plain=null;try{plain=BackupData.exportData(this,t,o);byte[] enc=BackupCrypto.encrypt(plain,pw);try(OutputStream out=getContentResolver().openOutputStream(target,"w")){if(out==null)throw new IOException("Нет доступа к файлу");out.write(enc);}return "Зашифрованная копия сохранена";}finally{Arrays.fill(pw,'\0');if(plain!=null)Arrays.fill(plain,(byte)0);}},v->status.setText((String)v));
        }
    }
    interface Work{Object run()throws Exception;}
    interface Done{void run(Object value);}
    private void run(Work work,Done after){busy=true;status.setText("Обработка…");worker.execute(()->{Object value=null;String error=null;try{value=work.run();}catch(Exception e){error=e instanceof javax.crypto.AEADBadTagException?"Неверный пароль или повреждённая копия":e.getMessage();if(error==null)error="Операция не завершена";}Object v=value;String err=error;runOnUiThread(()->{if(isDestroyed()||isFinishing())return;busy=false;if(err!=null)status.setText("Ошибка: "+err);else after.run(v);});});}
    private static byte[] readBounded(InputStream in)throws IOException{if(in==null)throw new IOException("Нет доступа к файлу");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[65536];int n;while((n=in.read(buf))!=-1){if(out.size()+n>BackupCrypto.MAX+BackupCrypto.HEADER+16)throw new IOException("Копия слишком большая");out.write(buf,0,n);}return out.toByteArray();}
    private void decrypt(Uri source,char[] pw){
        run(()->{byte[] plain=null;try(InputStream in=getContentResolver().openInputStream(source)){plain=BackupCrypto.decrypt(readBounded(in),pw);return BackupData.parse(plain);}finally{Arrays.fill(pw,'\0');if(plain!=null)Arrays.fill(plain,(byte)0);}},v->preview((JSONObject)v));
    }
    private void preview(JSONObject data){
        pending=data;LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(dp(12),dp(8),dp(12),dp(8));
        CheckBox settings=check("Применить настройки и состояние модулей",false),marks=check("Добавить закладки: "+data.optJSONArray("bookmarks").length(),true),t=check("Список вкладок: "+(data.has("tabs")?data.optJSONArray("tabs").length():0),false),o=check("Добавить офлайн-снимки: "+(data.has("offline")?data.optJSONArray("offline").length():0),false);
        t.setEnabled(data.has("tabs"));o.setEnabled(data.has("offline"));for(CheckBox c:new CheckBox[]{settings,marks,t,o})form.addView(c);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Копия проверена. Что восстановить?").setView(form).setNegativeButton("Отмена",null).setPositiveButton("Восстановить",(a,b)->{
            boolean ss=settings.isChecked(),mm=marks.isChecked(),tt=t.isChecked(),oo=o.isChecked();if(!ss&&!mm&&!tt&&!oo)return;
            run(()->BackupData.restore(this,data,ss,mm,tt,oo),v->{restored=true;setResult(RESULT_OK);status.setText((String)v);});
        }).create();d.setOnDismissListener(v->pending=null);d.show();if(d.getWindow()!=null)d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
    @Override public void onBackPressed(){if(!busy)super.onBackPressed();}
    @Override protected void onDestroy(){clearPassword();pending=null;worker.shutdown();super.onDestroy();}
}
