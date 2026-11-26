package com.aetherbolt.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import org.mozilla.geckoview.*;
import org.json.*;
import java.io.*;
import java.util.*;

final class FileTransfers {
    private static final int PICK=730, EXPORT=731;
    private static final String RECORDS="publicDownloadsV1";
    private final Activity ui;
    private final android.content.SharedPreferences prefs;
    private GeckoSession uploadSession;
    private GeckoSession.PromptDelegate.FilePrompt uploadPrompt;
    private GeckoResult<GeckoSession.PromptDelegate.PromptResponse> uploadResult;
    private Entry exportEntry;
    private boolean pickerOpen;
    private final Map<String,Job> downloads=new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong revision=new java.util.concurrent.atomic.AtomicLong();
    private Runnable changed;
    void setChanged(Runnable callback){changed=callback;}
    long revision(){return revision.get();}
    private void notifyChanged(){revision.incrementAndGet();ui.runOnUiThread(()->{if(!ui.isDestroyed()&&changed!=null)changed.run();});}
    static final class Job {
        final String id=UUID.randomUUID().toString(),name;final long total,started=System.currentTimeMillis();final WebResponse response;
        volatile long bytes;volatile boolean cancelled,done;volatile String status="Скачивание";
        Job(String name,long total,WebResponse response){this.name=name;this.total=total;this.response=response;}
        boolean busy(){return !done;}
    }
    List<Job> jobs(){ArrayList<Job> list=new ArrayList<>(downloads.values());list.sort(Comparator.comparingLong((Job j)->j.started).reversed());return list;}
    int activeCount(){int n=0;for(Job j:downloads.values())if(j.busy())n++;return n;}
    void cancel(Job j){if(j.done)return;j.cancelled=true;j.status="Отмена…";new Thread(()->close(j.response),"aether-download-cancel").start();}
    void dismiss(Job j){if(j.done){downloads.remove(j.id,j);notifyChanged();}}
    private long responseLength(WebResponse r){try{return Math.max(-1,Long.parseLong(r.headers.get("content-length")));}catch(Exception ex){return -1;}}
    private static long copyJob(InputStream in,OutputStream out,Job job)throws IOException{
        byte[] buf=new byte[65536];long total=0;int n;
        while(!job.cancelled&&(n=in.read(buf))!=-1){if(job.cancelled)break;out.write(buf,0,n);total+=n;job.bytes=total;}
        if(job.cancelled)throw new java.io.InterruptedIOException("Загрузка отменена");
        out.flush();return total;
    }
    FileTransfers(Activity a){ui=a;prefs=a.getSharedPreferences("aetherbolt",Activity.MODE_PRIVATE);}
    private void toast(String s){ui.runOnUiThread(()->{if(!ui.isDestroyed())Toast.makeText(ui,s,Toast.LENGTH_LONG).show();});}
    GeckoSession.PromptDelegate promptDelegate(){
        return new GeckoSession.PromptDelegate(){
            @Override public GeckoResult<PromptResponse> onFilePrompt(GeckoSession session,FilePrompt prompt){
                if(pickerOpen||ui.isFinishing()||ui.isDestroyed())return GeckoResult.fromValue(prompt.dismiss());
                if(prompt.type==FilePrompt.Type.FOLDER){toast("Выбор папки пока не поддерживается. Выберите отдельные файлы.");return GeckoResult.fromValue(prompt.dismiss());}
                GeckoResult<PromptResponse> result=new GeckoResult<>();uploadSession=session;uploadPrompt=prompt;uploadResult=result;
                Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                String[] accepted=prompt.mimeTypes;
                if(accepted!=null&&accepted.length>0){
                    ArrayList<String> valid=new ArrayList<>();boolean all=true;
                    for(String m:accepted){if(m==null||!m.matches("[a-zA-Z0-9!#$&^_.+*-]+/[a-zA-Z0-9!#$&^_.+*-]+")){all=false;break;}if(!valid.contains(m))valid.add(m);}
                    if(all&&valid.size()==1)i.setType(valid.get(0));
                    else if(all&&valid.size()>1)i.putExtra(Intent.EXTRA_MIME_TYPES,valid.toArray(new String[0]));
                }
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,prompt.type==FilePrompt.Type.MULTIPLE);
                try{pickerOpen=true;ui.startActivityForResult(i,PICK);}catch(RuntimeException e){pickerOpen=false;cancelUpload(session);toast("Не удалось открыть выбор файлов");}
                return result;
            }
        };
    }
    void cancelUpload(GeckoSession s){
        if(uploadSession!=s)return;
        GeckoSession.PromptDelegate.FilePrompt p=uploadPrompt;GeckoResult<GeckoSession.PromptDelegate.PromptResponse> r=uploadResult;
        uploadSession=null;uploadPrompt=null;uploadResult=null;
        if(p!=null&&r!=null&&!p.isComplete())r.complete(p.dismiss());
    }
    void destroy(){if(uploadSession!=null)cancelUpload(uploadSession);exportEntry=null;}
    boolean onActivityResult(int request,int result,Intent data){
        if(request==PICK){
            pickerOpen=false;GeckoSession.PromptDelegate.FilePrompt p=uploadPrompt;GeckoResult<GeckoSession.PromptDelegate.PromptResponse> r=uploadResult;
            uploadPrompt=null;uploadSession=null;uploadResult=null;
            if(p==null||r==null||p.isComplete())return true;
            if(result!=Activity.RESULT_OK||data==null){r.complete(p.dismiss());return true;}
            LinkedHashSet<Uri> uris=new LinkedHashSet<>();
            if(data.getClipData()!=null){ClipData clip=data.getClipData();for(int n=0;n<clip.getItemCount();n++){Uri u=clip.getItemAt(n).getUri();if(u!=null&&"content".equals(u.getScheme()))uris.add(u);}}
            if(data.getData()!=null&&"content".equals(data.getData().getScheme()))uris.add(data.getData());
            if(uris.isEmpty()){r.complete(p.dismiss());return true;}
            try{
                if(p.type==GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE)r.complete(p.confirm(ui.getApplicationContext(),uris.toArray(new Uri[0])));
                else r.complete(p.confirm(ui.getApplicationContext(),uris.iterator().next()));
            }catch(RuntimeException e){if(!p.isComplete())r.complete(p.dismiss());toast("Не удалось передать выбранный файл: "+e.getMessage());}
            return true;
        }
        if(request==EXPORT){Entry e=exportEntry;exportEntry=null;if(e!=null&&result==Activity.RESULT_OK&&data!=null&&data.getData()!=null){Uri target=data.getData();new Thread(()->{try(InputStream in=ui.getContentResolver().openInputStream(uri(e));OutputStream out=ui.getContentResolver().openOutputStream(target,"w")){if(in==null||out==null)throw new IOException("Нет доступа");copy(in,out);toast("Копия сохранена");}catch(Exception ex){toast("Не удалось сохранить копию: "+ex.getMessage());}},"aether-export").start();}return true;}
        return false;
    }
    static boolean sameOriginBlob(String target,String page){
        if(target==null||page==null||!target.startsWith("blob:"))return false;
        try{Uri a=Uri.parse(target.substring(5)),b=Uri.parse(page);String scheme=a.getScheme();if(!Arrays.asList("http","https","moz-extension").contains(scheme)||!scheme.equals(b.getScheme())||a.getHost()==null||!a.getHost().equalsIgnoreCase(b.getHost()))return false;return port(a)==port(b);}catch(RuntimeException ex){return false;}
    }
    private static int port(Uri u){return u.getPort()!=-1?u.getPort():"https".equals(u.getScheme())?443:"http".equals(u.getScheme())?80:-1;}
    static final class Entry{
        final String name,mime;final long bytes,date;final Uri publicUri;final File file;
        Entry(String n,String m,long b,Uri u,File f){this(n,m,b,u,f,f==null?System.currentTimeMillis():f.lastModified());}
        Entry(String n,String m,long b,Uri u,File f,long d){name=n;mime=m;bytes=b;publicUri=u;file=f;date=d;}
    }
    Uri uri(Entry e){return e.publicUri!=null?e.publicUri:FileProvider.getUriForFile(ui,ui.getPackageName()+".updates",e.file);}
    private static String mime(String type,String name){
        if(type!=null){String t=type.split(";",2)[0].trim();if(t.contains("/")&&!t.equals("application/octet-stream"))return t;}
        int n=name.lastIndexOf('.');String m=n<0?null:MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(n+1).toLowerCase(Locale.ROOT));return m==null?"application/octet-stream":m;
    }
    private static String filename(String name){String safe=name==null?"download":name.replaceAll("[\\p{Cntrl}/\\\\:*?\"<>|]","_").trim();if(safe.isEmpty()||safe.equals(".")||safe.equals(".."))safe="download";if(safe.length()>120)safe=safe.substring(safe.length()-120);return safe;}
    private File dir() throws IOException {File f=ui.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);if(f==null)throw new IOException("Хранилище недоступно");if(!f.isDirectory()&&!f.mkdirs())throw new IOException("Не удалось создать папку");return f;}
    private static long copy(InputStream in,OutputStream out)throws IOException{byte[] buf=new byte[65536];long total=0;int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);total+=n;}out.flush();return total;}
    private static void close(WebResponse r){try{if(r.body!=null)r.body.close();}catch(Exception ignored){}}
    void receive(WebResponse response){
        if(response.body==null){toast("Сайт не передал содержимое файла");return;}
        if(ui.isDestroyed()||ui.isFinishing()){close(response);return;}
        String name=filename(URLUtil.guessFileName(response.uri,response.headers.get("content-disposition"),response.headers.get("content-type")));
        String type=mime(response.headers.get("content-type"),name);
        Runnable save=()->{Job job=new Job(name,responseLength(response),response);downloads.put(job.id,job);notifyChanged();toast("Скачивание: "+name);new Thread(()->save(response,name,type,job),"aether-download").start();};
        if(response.skipConfirmation){save.run();return;}
        new AlertDialog.Builder(ui).setTitle("Скачать файл?").setMessage(name).setPositiveButton("Скачать",(d,w)->save.run()).setNegativeButton("Отмена",(d,w)->close(response)).setOnCancelListener(d->close(response)).show();
    }
    private void save(WebResponse response,String name,String type,Job job){
        Uri dest=null;File part=null;boolean finished=false;
        try(InputStream in=response.body){
            if(Build.VERSION.SDK_INT>=29){
                ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);values.put(MediaStore.MediaColumns.MIME_TYPE,type);values.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/AetherBolt");values.put(MediaStore.MediaColumns.IS_PENDING,1);
                dest=ui.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
                if(dest==null)throw new IOException("Не удалось создать файл Download");
                long bytes;try(OutputStream out=ui.getContentResolver().openOutputStream(dest,"w")){if(out==null)throw new IOException("Нет потока записи");bytes=copyJob(in,out,job);}
                if(job.cancelled)throw new java.io.InterruptedIOException("Отменено");job.status="Сохранение";ContentValues ready=new ContentValues();ready.put(MediaStore.MediaColumns.IS_PENDING,0);if(ui.getContentResolver().update(dest,ready,null,null)<1)throw new IOException("Не удалось завершить запись");
                String savedName=name;try(android.database.Cursor c=ui.getContentResolver().query(dest,new String[]{MediaStore.MediaColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())savedName=c.getString(0);}catch(Exception ignored){}
                record(new Entry(savedName,type,bytes,dest,null));finished=true;toast("Сохранено в Download/AetherBolt: "+savedName);
            }else{
                File folder=dir();part=File.createTempFile(".download-", ".part",folder);long bytes;
                try(OutputStream out=new FileOutputStream(part)){bytes=copyJob(in,out,job);}
                if(job.cancelled)throw new java.io.InterruptedIOException("Отменено");job.status="Сохранение";File target=new File(folder,System.currentTimeMillis()+"-"+name);if(!part.renameTo(target))throw new IOException("Не удалось завершить файл");
                finished=true;toast("Сохранено в Загрузках браузера: "+target.getName());
            }
        }catch(Exception e){job.status=job.cancelled?"Отменено":"Ошибка: "+String.valueOf(e.getMessage());toast(job.status);}finally{if(!finished&&dest!=null)try{ui.getContentResolver().delete(dest,null,null);}catch(Exception ignored){}if(part!=null&&part.exists())part.delete();job.done=true;if(finished)downloads.remove(job.id,job);notifyChanged();}
    }
    private synchronized void record(Entry e)throws JSONException{
        JSONArray old=records();JSONObject item=new JSONObject().put("uri",e.publicUri.toString()).put("name",e.name).put("mime",e.mime).put("bytes",e.bytes).put("date",e.date);
        JSONArray next=new JSONArray().put(item);for(int i=0;i<Math.min(old.length(),999);i++)next.put(old.get(i));prefs.edit().putString(RECORDS,next.toString()).apply();
    }
    private JSONArray records(){try{return new JSONArray(prefs.getString(RECORDS,"[]"));}catch(JSONException ex){return new JSONArray();}}
    List<Entry> entries(){
        ArrayList<Entry> list=new ArrayList<>();JSONArray a=records();
        for(int n=0;n<a.length();n++){JSONObject o=a.optJSONObject(n);if(o==null)continue;Uri u=Uri.parse(o.optString("uri"));if(!"content".equals(u.getScheme())||!"media".equals(u.getAuthority()))continue;list.add(new Entry(o.optString("name"),o.optString("mime","application/octet-stream"),o.optLong("bytes"),u,null,o.optLong("date",0)));}
        try{File[] old=dir().listFiles();if(old!=null){Arrays.sort(old,Comparator.comparingLong(File::lastModified).reversed());for(File f:old)if(f.isFile()&&!f.getName().endsWith(".part"))list.add(new Entry(f.getName(),mime(null,f.getName()),f.length(),null,f));}}catch(IOException ignored){}
        list.sort(Comparator.comparingLong((Entry e)->e.date).reversed());return list;
    }
    void open(Entry e){try{Uri u=uri(e);Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(u,e.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);ui.startActivity(Intent.createChooser(i,"Открыть файл"));}catch(Exception ex){toast("Не удалось открыть файл: "+ex.getMessage());}}
    void share(Entry e){try{Uri u=uri(e);Intent i=new Intent(Intent.ACTION_SEND).setType(e.mime).putExtra(Intent.EXTRA_STREAM,u).setClipData(ClipData.newRawUri(e.name,u)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);ui.startActivity(Intent.createChooser(i,"Поделиться файлом"));}catch(Exception ex){toast("Не удалось поделиться: "+ex.getMessage());}}
    void export(Entry e){if(exportEntry!=null)return;exportEntry=e;try{ui.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(e.mime).addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,e.name),EXPORT);}catch(Exception ex){exportEntry=null;toast("Не удалось открыть выбор папки");}}
    synchronized boolean delete(Entry e){try{
        if(e.file!=null){boolean ok=e.file.delete();if(ok)notifyChanged();return ok;}ui.getContentResolver().delete(e.publicUri,null,null);JSONArray a=records(),next=new JSONArray();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&!e.publicUri.toString().equals(o.optString("uri")))next.put(o);}prefs.edit().putString(RECORDS,next.toString()).apply();notifyChanged();return true;
    }catch(Exception ex){return false;}}
}
