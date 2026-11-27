package com.aetherbolt.browser;
import android.content.*;
import android.net.Uri;
import org.json.*;
import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

final class BackupData {
    // Deliberate allowlist. No network/security policy or executable configuration.
    private static final String[] BOOL={"videoEco","videoAllPages","videoSuspend","videoHwPriority","allowSilentPreview","images","desktop","zoom"};
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("aetherbolt",Context.MODE_PRIVATE);}
    private static JSONArray urls(JSONArray input,int cap,boolean strict)throws Exception{
        if(strict&&input.length()>cap)throw new IOException("Слишком много адресов");
        JSONArray output=new JSONArray();Set<String> seen=new HashSet<>();
        for(int i=0;i<Math.min(cap,input.length());i++){
            String u=input.optString(i,"");
            if(u.length()>4096||Router.parse(u).kind!=Router.Kind.WEB){if(strict)throw new IOException("Недопустимый URL");continue;}
            if(seen.add(u))output.put(u);
        }return output;
    }
    static byte[] exportData(Context c,boolean tabs,boolean offline)throws Exception{
        SharedPreferences p=prefs(c);JSONObject options=new JSONObject();
        for(String k:BOOL)if(p.contains(k))options.put(k,p.getBoolean(k,false));
        for(String[] m:ModuleCatalog.LOCAL)options.put("module."+m[0],ModuleCatalog.enabled(c,m[0]));
        options.put("appearance",p.getString("appearance","Космос"));options.put("searchLanguage",p.getString("searchLanguage","all"));options.put("downloadParallel",p.getInt("downloadParallel",2));
        JSONObject root=new JSONObject().put("schema",1).put("options",options).put("bookmarks",urls(new JSONArray(p.getString("bookmarks","[]")),200,false));
        if(tabs)root.put("tabs",urls(new JSONArray(p.getString("workspaceTabs","[]")),12,false));
        if(offline){JSONArray list=new JSONArray();for(OfflineStore.Page page:OfflineStore.list(c))list.put(new JSONObject().put("title",page.title).put("url",page.url).put("text",page.text).put("date",page.date));root.put("offline",list);}
        byte[] result=root.toString().getBytes(StandardCharsets.UTF_8);if(result.length>BackupCrypto.MAX)throw new IOException("Лимит резервной копии");return result;
    }
    static JSONObject parse(byte[] plain)throws Exception{
        JSONObject root=new JSONObject(new String(plain,StandardCharsets.UTF_8));if(root.getInt("schema")!=1)throw new IOException("Версия не поддерживается");
        JSONObject raw=root.getJSONObject("options"),safe=new JSONObject();
        for(String k:BOOL)if(raw.has(k)){Object v=raw.get(k);if(!(v instanceof Boolean))throw new IOException("Некорректная настройка");safe.put(k,v);}
        for(String[] m:ModuleCatalog.LOCAL){String k="module."+m[0];if(raw.has(k)){Object v=raw.get(k);if(!(v instanceof Boolean))throw new IOException("Некорректный модуль");safe.put(k,v);}}
        if(raw.has("appearance")){String v=raw.getString("appearance");if(!Arrays.asList("Космос","AMOLED","Графит").contains(v))throw new IOException("Некорректная тема");safe.put("appearance",v);}
        if(raw.has("searchLanguage")){String v=raw.getString("searchLanguage");if(!Arrays.asList("all","uk","ru","en").contains(v))throw new IOException("Некорректный язык");safe.put("searchLanguage",v);}
        if(raw.has("downloadParallel")){int v=raw.getInt("downloadParallel");if(v<1||v>3)throw new IOException("Некорректный лимит");safe.put("downloadParallel",v);}
        JSONObject validated=new JSONObject().put("options",safe).put("bookmarks",urls(root.getJSONArray("bookmarks"),200,true));
        if(root.has("tabs"))validated.put("tabs",urls(root.getJSONArray("tabs"),12,true));
        if(root.has("offline")){
            JSONArray list=root.getJSONArray("offline");if(list.length()>OfflineStore.MAX_COUNT)throw new IOException("Слишком много снимков");
            JSONArray clean=new JSONArray();
            for(int i=0;i<list.length();i++){JSONObject j=list.getJSONObject(i);String title=j.getString("title"),url=j.getString("url"),text=j.getString("text");
                if(title.length()>512||url.length()>4096||text.length()>180000||text.trim().isEmpty()||Router.parse(url).kind!=Router.Kind.WEB)throw new IOException("Некорректный снимок");
                Uri u=Uri.parse(url);if(u.getEncodedQuery()!=null||u.getEncodedFragment()!=null||u.getUserInfo()!=null)throw new IOException("Некорректный URL снимка");
                clean.put(new JSONObject().put("title",title).put("url",url).put("text",text).put("date",j.getLong("date")));
            }validated.put("offline",clean);
        }
        return validated;
    }
    static String restore(Context c,JSONObject root,boolean settings,boolean marks,boolean tabs,boolean offline)throws Exception{
        SharedPreferences p=prefs(c);SharedPreferences.Editor e=p.edit();
        if(settings){JSONObject options=root.getJSONObject("options");Iterator<String> keys=options.keys();while(keys.hasNext()){String k=keys.next();Object v=options.get(k);if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof String)e.putString(k,(String)v);}}
        int added=0;
        if(marks){
            JSONArray existing=new JSONArray(p.getString("bookmarks","[]"));LinkedHashSet<String> merged=new LinkedHashSet<>();
            for(int i=0;i<existing.length();i++)merged.add(existing.getString(i));
            JSONArray incoming=root.getJSONArray("bookmarks");for(int i=0;i<incoming.length();i++)if(merged.add(incoming.getString(i)))added++;
            if(merged.size()>200)throw new IOException("Лимит закладок: сначала освободите место");e.putString("bookmarks",new JSONArray(merged).toString());
        }
        if(tabs&&root.has("tabs"))e.putString("backupRecoveredTabs",root.getJSONArray("tabs").toString());
        int snapshots=offline&&root.has("offline")?OfflineStore.merge(c,root.getJSONArray("offline")):0;
        if(!e.commit())throw new IOException("Настройки не сохранены; импортированные снимки могли сохраниться");
        return "Новых закладок: "+added+"; снимков: "+snapshots+". Вкладки доступны в Backup Center.";
    }
}
