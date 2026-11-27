package com.aetherbolt.browser;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class OfflineLibraryActivity extends Activity {
    private static final int EXPORT=970;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private List<OfflineStore.Page> pages=Collections.emptyList();
    private OfflineStore.Page opened,export;
    private LinearLayout root,list,tools;
    private TextView status;
    private EditText search;
    private boolean busy;
    private int font=17;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextColor(0xffedf2ff);t.setTextSize(size);t.setPadding(dp(10),dp(8),dp(10),dp(8));return t;}
    private void button(LinearLayout r,String name,Runnable click){Button b=new Button(this);b.setText(name);b.setAllCaps(false);b.setOnClickListener(v->{if(!busy)click.run();});r.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);if(!ModuleCatalog.guard(this,"offline")){finish();return;}
        root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(12),dp(8),dp(12),dp(8));root.setBackgroundColor(0xff0b1020);
        root.addView(text("Offline Browser • библиотека",22));tools=new LinearLayout(this);root.addView(tools);
        search=new EditText(this);search.setTextColor(0xffedf2ff);search.setHintTextColor(0xffa0abc5);search.setHint("Поиск по названию и тексту");search.setSingleLine(true);search.setSaveEnabled(false);root.addView(search);
        status=text("",13);root.addView(status);
        ScrollView scroll=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(1);scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){if(opened==null)render();}public void afterTextChanged(Editable e){}});
        load();
    }
    interface Work{void run()throws Exception;}
    private void run(Work work,Runnable after){
        if(busy)return;busy=true;status.setText("Подготовка…");worker.execute(()->{String err=null;try{work.run();}catch(Exception e){err=e.getMessage();}String error=err;runOnUiThread(()->{if(isDestroyed())return;busy=false;if(error!=null)status.setText("Ошибка: "+error);else after.run();});});
    }
    private void load(){run(()->pages=OfflineStore.list(getApplicationContext()),()->{opened=null;render();});}
    private void render(){
        if(list==null)return;tools.removeAllViews();list.removeAllViews();search.setVisibility(View.VISIBLE);
        button(tools,"Назад",this::finish);button(tools,"Обновить",this::load);button(tools,"Очистить",this::confirmClear);
        long used=0;for(OfflineStore.Page p:pages)used+=p.bytes;
        status.setText(pages.size()+" / 100 • "+android.text.format.Formatter.formatShortFileSize(this,used)+" / 16 МиБ");
        String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);int shown=0;
        for(OfflineStore.Page p:pages){if(!q.isEmpty()&&!p.title.toLowerCase(Locale.ROOT).contains(q)&&!p.text.toLowerCase(Locale.ROOT).contains(q)&&!p.url.toLowerCase(Locale.ROOT).contains(q))continue;
            shown++;TextView row=text(p.title+"\n"+p.url+"\n"+new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(new Date(p.date)),15);row.setOnClickListener(v->{if(!busy){opened=p;reader();}});list.addView(row);
        }
        if(shown==0)list.addView(text("Нет подходящих снимков",15));
    }
    private void reader(){
        if(opened==null)return;tools.removeAllViews();list.removeAllViews();search.setVisibility(View.GONE);
        button(tools,"‹ Список",()->{opened=null;render();});button(tools,"A−",()->{font=Math.max(12,font-1);reader();});button(tools,"A+",()->{font=Math.min(26,font+1);reader();});
        button(tools,"Экспорт",this::exportText);button(tools,"Удалить",this::delete);
        status.setText("Офлайн • текстовый снимок");
        list.addView(text(opened.title,22));list.addView(text(opened.url,12));TextView t=text(opened.text,font);t.setTextIsSelectable(true);t.setSaveEnabled(false);list.addView(t);
    }
    private void exportText(){
        if(opened==null)return;export=opened;
        new AlertDialog.Builder(this).setTitle("Экспорт текста?").setMessage("Файл будет незашифрованным. Проверьте, нет ли в тексте личных данных.").setNegativeButton("Отмена",(d,w)->export=null).setPositiveButton("Выбрать файл",(d,w)->{
            try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"AetherBolt-page.txt"),EXPORT);}catch(Exception e){export=null;status.setText("Не удалось открыть выбор файла");}
        }).show();
    }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);if(req!=EXPORT)return;OfflineStore.Page p=export;export=null;
        if(result!=RESULT_OK||data==null||data.getData()==null||p==null)return;Uri target=data.getData();
        run(()->{try(OutputStream out=getContentResolver().openOutputStream(target,"w")){if(out==null)throw new IOException("Нет доступа");out.write((p.title+"\n"+p.url+"\n\n"+p.text).getBytes(StandardCharsets.UTF_8));}},()->status.setText("Текст экспортирован"));
    }
    private void delete(){if(opened==null)return;String id=opened.id;new AlertDialog.Builder(this).setTitle("Удалить снимок?").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->run(()->OfflineStore.remove(getApplicationContext(),id),this::load)).show();}
    private void confirmClear(){new AlertDialog.Builder(this).setTitle("Удалить все офлайн-снимки?").setMessage("Экспортированные файлы не удаляются.").setNegativeButton("Отмена",null).setPositiveButton("Очистить",(d,w)->run(()->OfflineStore.clear(getApplicationContext()),this::load)).show();}
    @Override public void onBackPressed(){if(busy)return;if(opened!=null){opened=null;render();}else super.onBackPressed();}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
