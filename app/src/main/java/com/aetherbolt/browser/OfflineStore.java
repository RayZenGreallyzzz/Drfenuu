package com.aetherbolt.browser;
import android.content.Context;
import android.net.Uri;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class OfflineStore {
    static final long LIMIT=16L*1024*1024;
    static final int MAX_COUNT=100, MAX_FILE=1024*1024;
    static final class Page {
        final String id,title,url,text;final long date,bytes;
        Page(String id,String title,String url,String text,long date,long bytes){this.id=id;this.title=title;this.url=url;this.text=text;this.date=date;this.bytes=bytes;}
    }
    private static File folder(Context c)throws IOException{File f=new File(c.getFilesDir(),"offline-pages");if(!f.isDirectory()&&!f.mkdirs())throw new IOException("Хранилище недоступно");return f;}
    private static File[] files(Context c)throws IOException{File[] f=folder(c).listFiles((dir,name)->name.matches("[0-9a-f-]{36}\\.json"));if(f==null)throw new IOException("Хранилище недоступно");return f;}
    private static File file(Context c,String id)throws IOException{if(id==null||!id.matches("[0-9a-f-]{36}"))throw new IOException("Некорректный ID");return new File(folder(c),id+".json");}
    static synchronized void save(Context c,String title,String url,String text)throws Exception{
        if(text==null||text.trim().isEmpty()||text.length()>180000||Router.parse(url).kind!=Router.Kind.WEB)throw new IOException("Некорректный снимок");
        Uri u=Uri.parse(url);
        // Do not persist URL userinfo, query or fragment. Path can still contain sensitive data.
        String host=u.getHost();if(host==null)throw new IOException("Некорректный адрес");
        String safe=u.getScheme()+"://"+host+(u.getPort()>0?":"+u.getPort():"")+(u.getEncodedPath()==null?"":u.getEncodedPath());
        if(safe.length()>4096)safe=safe.substring(0,4096);
        String id=UUID.randomUUID().toString();String label=title==null||title.trim().isEmpty()?host:title.substring(0,Math.min(512,title.length()));
        byte[] data=new JSONObject().put("title",label).put("url",safe).put("text",text).put("date",System.currentTimeMillis()).toString().getBytes(StandardCharsets.UTF_8);
        File[] existing=files(c);long used=0;for(File f:existing)used+=f.length();
        if(data.length>MAX_FILE||existing.length>=MAX_COUNT||used+data.length>LIMIT)throw new IOException("Лимит библиотеки; удалите ненужные снимки");
        AtomicFile a=new AtomicFile(file(c,id));FileOutputStream out=a.startWrite();try{out.write(data);a.finishWrite(out);}catch(Exception e){a.failWrite(out);throw e;}
    }
    private static Page readFile(File f)throws Exception{
        if(f.length()>MAX_FILE)throw new IOException("Слишком большой снимок");
        byte[] bytes;try(InputStream in=new AtomicFile(f).openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>MAX_FILE)throw new IOException("Лимит");out.write(b,0,n);}bytes=out.toByteArray();}
        JSONObject j=new JSONObject(new String(bytes,StandardCharsets.UTF_8));return new Page(f.getName().substring(0,36),j.getString("title"),j.getString("url"),j.getString("text"),j.getLong("date"),f.length());
    }
    static synchronized List<Page> list(Context c)throws IOException{
        ArrayList<Page> result=new ArrayList<>();File[] all=files(c);Arrays.sort(all,Comparator.comparingLong(File::lastModified).reversed());
        long size=0;for(File f:all){if(result.size()>=MAX_COUNT||size+f.length()>LIMIT)break;try{result.add(readFile(f));size+=f.length();}catch(Exception ignored){}}
        result.sort(Comparator.comparingLong((Page p)->p.date).reversed());return result;
    }
    static synchronized void remove(Context c,String id)throws IOException{File f=file(c,id);new AtomicFile(f).delete();if(f.exists())throw new IOException("Не удалось удалить");}
    static synchronized void clear(Context c)throws IOException{for(File f:files(c))remove(c,f.getName().substring(0,36));}
}
