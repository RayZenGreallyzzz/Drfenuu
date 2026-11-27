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
    static synchronized int merge(Context c,org.json.JSONArray pages)throws Exception{
        if(pages.length()>MAX_COUNT)throw new IOException("Слишком много снимков");
        List<Page> existing=list(c);Set<String> fingerprints=new HashSet<>();
        for(Page p:existing)fingerprints.add(fingerprint(p.title,p.url,p.text));
        File[] old=files(c);long used=0;for(File f:old)used+=f.length();
        ArrayList<byte[]> pending=new ArrayList<>();
        for(int i=0;i<pages.length();i++){
            JSONObject j=pages.getJSONObject(i);String title=j.getString("title"),url=j.getString("url"),text=j.getString("text");long date=j.getLong("date");
            if(title.length()>512||url.length()>4096||text.length()>180000||text.trim().isEmpty()||Router.parse(url).kind!=Router.Kind.WEB)throw new IOException("Некорректный снимок");
            Uri u=Uri.parse(url);if(u.getEncodedQuery()!=null||u.getEncodedFragment()!=null||u.getUserInfo()!=null)throw new IOException("Некорректный URL снимка");
            if(!fingerprints.add(fingerprint(title,url,text)))continue;
            byte[] bytes=new JSONObject().put("title",title).put("url",url).put("text",text).put("date",date).toString().getBytes(StandardCharsets.UTF_8);
            used+=bytes.length;if(bytes.length>MAX_FILE||used>LIMIT||old.length+pending.size()+1>MAX_COUNT)throw new IOException("Недостаточно места в офлайн-библиотеке");
            pending.add(bytes);
        }
        ArrayList<String> created=new ArrayList<>();
        try{for(byte[] bytes:pending){String id=UUID.randomUUID().toString();created.add(id);AtomicFile a=new AtomicFile(file(c,id));FileOutputStream out=a.startWrite();try{out.write(bytes);a.finishWrite(out);}catch(Exception e){a.failWrite(out);throw e;}}}
        catch(Exception e){for(String id:created)try{remove(c,id);}catch(Exception ignored){}throw e;}
        return pending.size();
    }
    private static String fingerprint(String title,String url,String text)throws Exception{
        java.security.MessageDigest md=java.security.MessageDigest.getInstance("SHA-256");
        return android.util.Base64.encodeToString(md.digest(new org.json.JSONArray().put(title).put(url).put(text).toString().getBytes(StandardCharsets.UTF_8)),android.util.Base64.NO_WRAP);
    }
    static synchronized void remove(Context c,String id)throws IOException{File f=file(c,id);new AtomicFile(f).delete();if(f.exists())throw new IOException("Не удалось удалить");}
    static synchronized void clear(Context c)throws IOException{for(File f:files(c))remove(c,f.getName().substring(0,36));}
}
