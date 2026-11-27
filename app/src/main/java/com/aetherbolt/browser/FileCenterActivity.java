package com.aetherbolt.browser;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class FileCenterActivity extends Activity {
    private static final int ROOT=940,DEST=941,OPEN=942,ZIP=943;
    private static final int TEXT=0xffedf2ff,BG=0xff0b1020;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<Node> rows=new ArrayList<>();
    private final ArrayDeque<Uri> parents=new ArrayDeque<>();
    private final LinkedHashMap<Uri,Node> selected=new LinkedHashMap<>();
    private Uri tree,folder;
    private TextView status,path;
    private EditText search;
    private LinearLayout list,actions;
    private boolean busy,descending,move;
    private int sort=0;
    private List<Node> pending=Collections.emptyList();
    private static final class Node {
        final Uri uri;final String name,mime;final long size,date;final int flags;
        Node(Uri u,String n,String m,long s,long d,int f){uri=u;name=n;mime=m;size=s;date=d;flags=f;}
        boolean dir(){return Document.MIME_TYPE_DIR.equals(mime);}
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(TEXT);v.setPadding(dp(8),dp(8),dp(8),dp(8));return v;}
    private void button(LinearLayout p,String title,Runnable r){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setOnClickListener(v->r.run());p.addView(b,new LinearLayout.LayoutParams(0,dp(50),1));}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);return r;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);if(!ModuleCatalog.guard(this,"files")){finish();return;}LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(12),dp(8),dp(12),dp(8));root.setBackgroundColor(BG);
        root.addView(text("File & Document Center",22));
        LinearLayout top=row();button(top,"Папка",()->choose(ROOT));button(top,"Открыть файл",()->choose(OPEN));button(top,"Назад",this::up);root.addView(top);
        path=text("Выберите папку Android",13);root.addView(path);status=text("Доступ только к выбранным файлам и папкам",13);root.addView(status);
        search=new EditText(this);search.setTextColor(TEXT);search.setHintTextColor(0xff9aa6c0);search.setSingleLine(true);search.setHint("Поиск в папке");root.addView(search);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){render();}public void afterTextChanged(Editable e){}});
        LinearLayout tools=row();button(tools,"Сортировка",this::sortDialog);button(tools,"+ Папка",()->nameDialog("Создать папку","Новая папка",name->createFolder(name)));button(tools,"Снять выбор",()->{selected.clear();render();});root.addView(tools);
        actions=row();button(actions,"Копировать",()->prepareTransfer(false));button(actions,"Переместить",()->prepareTransfer(true));button(actions,"ZIP",this::makeZip);button(actions,"Удалить",this::deleteSelected);root.addView(actions);
        ScrollView scroll=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(1);scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }
    private void choose(int code){
        if(busy)return;try{
            Intent i=code==OPEN?new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE):new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(i,code);
        }catch(Exception e){status.setText("Системный выбор недоступен");}
    }
    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);
        if(res!=RESULT_OK||data==null||data.getData()==null){if(req==DEST||req==ZIP)pending=Collections.emptyList();return;}
        Uri u=data.getData();
        if(req==ROOT){tree=u;folder=DocumentsContract.buildDocumentUriUsingTree(u,DocumentsContract.getTreeDocumentId(u));parents.clear();refresh();}
        else if(req==OPEN)open(u,null,getContentResolver().getType(u));
        else if(req==DEST){List<Node> source=pending;pending=Collections.emptyList();Uri destination=DocumentsContract.buildDocumentUriUsingTree(u,DocumentsContract.getTreeDocumentId(u));boolean mv=move;task(()->transfer(source,destination,mv),this::refresh);}
        else if(req==ZIP){List<Node> source=pending;pending=Collections.emptyList();task(()->writeZip(source,u),this::refresh);}
    }
    interface Work {String run()throws Exception;}
    private void task(Work work,Runnable after){
        if(busy||isDestroyed())return;busy=true;status.setText("Выполняется…");
        worker.execute(()->{String msg;try{msg=work.run();}catch(Exception e){msg="Ошибка: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}
            final String result=msg;runOnUiThread(()->{if(isDestroyed())return;busy=false;status.setText(result);if(after!=null)after.run();});});
    }
    private void refresh(){
        if(folder==null||busy)return;selected.clear();final Uri target=folder,root=tree;final ArrayList<Node> result=new ArrayList<>();
        task(()->{
            String id=DocumentsContract.getDocumentId(target);
            Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(root,id);
            String[] cols={Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,Document.COLUMN_MIME_TYPE,Document.COLUMN_SIZE,Document.COLUMN_LAST_MODIFIED,Document.COLUMN_FLAGS};
            try(Cursor c=getContentResolver().query(children,cols,null,null,null)){
                if(c==null)throw new IOException("Папка недоступна");
                while(c.moveToNext()&&result.size()<5000)result.add(new Node(DocumentsContract.buildDocumentUriUsingTree(root,c.getString(0)),c.getString(1)==null?"Без имени":c.getString(1),c.getString(2)==null?"application/octet-stream":c.getString(2),c.getLong(3),c.getLong(4),c.getInt(5)));
            }
            return "Объектов: "+result.size()+(result.size()==5000?" (лимит отображения)":"");
        },()->{rows.clear();rows.addAll(result);path.setText("Папка: "+DocumentsContract.getDocumentId(target));render();});
    }
    private void up(){if(busy)return;if(parents.isEmpty()){finish();return;}folder=parents.removeLast();refresh();}
    @Override public void onBackPressed(){up();}
    private void render(){
        if(list==null)return;list.removeAllViews();String q=search.getText().toString().toLowerCase(Locale.ROOT);
        ArrayList<Node> copy=new ArrayList<>(rows);
        Comparator<Node> order=sort==1?Comparator.comparingLong(n->n.size):sort==2?Comparator.comparingLong(n->n.date):Comparator.comparing(n->n.name,String.CASE_INSENSITIVE_ORDER);
        if(descending)order=order.reversed();copy.sort(Comparator.comparing((Node n)->!n.dir()).thenComparing(order));
        int shown=0;
        for(Node n:copy){if(!n.name.toLowerCase(Locale.ROOT).contains(q))continue;if(shown++>=500){list.addView(text("Уточните поиск для остальных файлов",13));break;}
            LinearLayout r=row();CheckBox check=new CheckBox(this);check.setChecked(selected.containsKey(n.uri));check.setOnCheckedChangeListener((b,on)->{if(on)selected.put(n.uri,n);else selected.remove(n.uri);updateActions();});r.addView(check);
            TextView label=text((n.dir()?"▤  ":"•  ")+n.name+(n.dir()?"":"\n"+android.text.format.Formatter.formatShortFileSize(this,Math.max(0,n.size))),15);r.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            label.setOnClickListener(v->{if(busy)return;if(n.dir()){parents.addLast(folder);folder=n.uri;refresh();}else open(n.uri,n.name,n.mime);});
            Button more=new Button(this);more.setText("⋮");more.setOnClickListener(v->details(n));r.addView(more,new LinearLayout.LayoutParams(dp(48),dp(52)));list.addView(r);
        }
        updateActions();
    }
    private void updateActions(){actions.setContentDescription("Выбрано: "+selected.size());}
    private void sortDialog(){new AlertDialog.Builder(this).setTitle("Сортировка").setItems(new String[]{"Имя ↑","Имя ↓","Размер ↑","Размер ↓","Дата ↑","Дата ↓"},(d,n)->{sort=n/2;descending=n%2==1;render();}).show();}
    private void open(Uri uri,String name,String mime){
        if(!"content".equals(uri.getScheme()))return;
        if(MediaCenterActivity.isMedia(name,mime))startActivity(new Intent(this,MediaCenterActivity.class).setDataAndType(uri,mime==null?"application/octet-stream":mime).putExtra("title",name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        else startActivity(new Intent(this,DocumentCenterActivity.class).setDataAndType(uri,mime==null?"application/octet-stream":mime).putExtra("title",name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
    }
    interface Named{void run(String name);}
    private void nameDialog(String title,String initial,Named action){
        if(busy||folder==null)return;EditText input=new EditText(this);input.setText(initial);input.setSingleLine(true);
        AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("Отмена",null).setPositiveButton("OK",null).create();
        d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{String n=input.getText().toString().trim();if(!validName(n)){input.setError("Недопустимое имя");return;}d.dismiss();action.run(n);}));d.show();
    }
    static boolean validName(String n){return n!=null&&!n.isEmpty()&&n.length()<=180&&!n.equals(".")&&!n.equals("..")&&!n.matches(".*[\\\\/\\p{Cntrl}].*");}
    private void createFolder(String name){Uri target=folder;task(()->{if(DocumentsContract.createDocument(getContentResolver(),target,Document.MIME_TYPE_DIR,name)==null)throw new IOException("Не удалось создать папку");return "Папка создана";},this::refresh);}
    private void details(Node n){
        if(busy)return;
        new AlertDialog.Builder(this).setTitle(n.name).setItems(new String[]{"Переименовать","Поделиться","Открыть внешним приложением"},(d,i)->{
            if(i==0)nameDialog("Переименовать",n.name,name->task(()->{if(DocumentsContract.renameDocument(getContentResolver(),n.uri,name)==null)throw new IOException("Переименование не поддерживается");return "Переименовано";},this::refresh));
            else if(!n.dir())try{Intent intent=i==1?new Intent(Intent.ACTION_SEND).setType(n.mime).putExtra(Intent.EXTRA_STREAM,n.uri):new Intent(Intent.ACTION_VIEW).setDataAndType(n.uri,n.mime);if(i==1)intent.setClipData(ClipData.newRawUri(n.name,n.uri));startActivity(Intent.createChooser(intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"Выберите приложение"));}catch(Exception e){status.setText("Нет подходящего приложения");}
        }).show();
    }
    private List<Node> chosenFiles(){if(busy||selected.isEmpty()){status.setText("Выберите файлы");return null;}for(Node n:selected.values())if(n.dir()){status.setText("Для этой операции выберите только файлы");return null;}return new ArrayList<>(selected.values());}
    private void prepareTransfer(boolean mv){
        List<Node> source=chosenFiles();if(source==null)return;
        Runnable go=()->{pending=source;move=mv;choose(DEST);};
        if(mv)new AlertDialog.Builder(this).setTitle("Переместить файлы?").setMessage("Только если поставщик Android поддерживает безопасное перемещение. Иначе исходные файлы останутся на месте.").setNegativeButton("Отмена",null).setPositiveButton("Выбрать папку",(d,w)->go.run()).show();else go.run();
    }
    private String transfer(List<Node> source,Uri dest,boolean mv){
        int ok=0,fail=0;Uri parent=folder;
        for(Node n:source){if(Thread.currentThread().isInterrupted())break;Uri made=null;
            try{
                if(mv){if((n.flags&Document.FLAG_SUPPORTS_MOVE)==0)throw new IOException("Перемещение не поддерживается");made=DocumentsContract.moveDocument(getContentResolver(),n.uri,parent,dest);if(made==null)throw new IOException("Не перемещено");}
                else{
                    if(!validName(n.name))throw new IOException("Имя недопустимо");
                    made=DocumentsContract.createDocument(getContentResolver(),dest,n.mime,n.name);if(made==null)throw new IOException("Не создан файл");
                    try(InputStream in=getContentResolver().openInputStream(n.uri);OutputStream out=getContentResolver().openOutputStream(made,"w")){copy(in,out);}
                }ok++;
            }catch(Exception e){fail++;if(!mv&&made!=null)try{DocumentsContract.deleteDocument(getContentResolver(),made);}catch(Exception ignored){}}
        }
        return (mv?"Перемещено: ":"Скопировано: ")+ok+"; ошибок: "+fail;
    }
    private void deleteSelected(){
        List<Node> source=chosenFiles();if(source==null)return;
        new AlertDialog.Builder(this).setTitle("Удалить "+source.size()+" файлов?").setMessage("Действие может быть необратимым. Папки не удаляются.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->task(()->{int ok=0;for(Node n:source){if(Thread.currentThread().isInterrupted())break;try{if(DocumentsContract.deleteDocument(getContentResolver(),n.uri))ok++;}catch(Exception ignored){}}return "Удалено: "+ok+" из "+source.size();},this::refresh)).show();
    }
    private void makeZip(){
        List<Node> source=chosenFiles();if(source==null)return;pending=source;
        try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"AetherBolt.zip"),ZIP);}catch(Exception e){pending=Collections.emptyList();status.setText("Не удалось выбрать ZIP");}
    }
    private String writeZip(List<Node> source,Uri target)throws Exception{
        for(Node n:source)if(n.uri.equals(target))throw new IOException("Нельзя записать архив поверх исходного файла");
        HashSet<String> names=new HashSet<>();
        try(OutputStream raw=getContentResolver().openOutputStream(target,"w")){
            if(raw==null)throw new IOException("Нет доступа");
            try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(raw)){
                for(Node n:source){String name=n.name;if(!validName(name))throw new IOException("Недопустимое имя");String unique=name;int i=1;while(!names.add(unique))unique=(i++)+"-"+name;
                    zip.putNextEntry(new java.util.zip.ZipEntry(unique));try(InputStream in=getContentResolver().openInputStream(n.uri)){copy(in,zip);}zip.closeEntry();}
            }
        }catch(Exception e){throw new IOException("Архив не завершён; удалите неполный файл",e);}
        return "ZIP создан: "+source.size()+" файлов";
    }
    static long copy(InputStream in,OutputStream out)throws IOException{
        if(in==null||out==null)throw new IOException("Нет доступа к потоку");byte[] buf=new byte[65536];long total=0;int n;
        while((n=in.read(buf))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();out.write(buf,0,n);total+=n;}out.flush();return total;
    }
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
