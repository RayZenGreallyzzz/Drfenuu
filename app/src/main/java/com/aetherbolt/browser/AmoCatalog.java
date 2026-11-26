package com.aetherbolt.browser;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class AmoCatalog {
    static final class Addon {
        final String name,summary,version,download,page;
        Addon(String n,String s,String v,String d,String p){name=n;summary=s;version=v;download=d;page=p;}
    }
    static final class Result{
        final List<Addon> items; final int count; final boolean hasNext;
        Result(List<Addon> i,int c,boolean n){items=i;count=c;hasNext=n;}
    }
    private static String localized(Object obj){
        if(obj instanceof String)return (String)obj;
        if(!(obj instanceof JSONObject))return "";
        JSONObject j=(JSONObject)obj;for(String l:new String[]{"ru","uk","en-US","en"}){String s=j.optString(l,"");if(!s.isEmpty())return s;}
        Iterator<String> keys=j.keys();return keys.hasNext()?j.optString(keys.next(),""):"";
    }
    private static boolean official(String url){
        try{URI u=new URI(url);return "https".equalsIgnoreCase(u.getScheme())&&"addons.mozilla.org".equalsIgnoreCase(u.getHost())&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443);}catch(Exception e){return false;}
    }
    static Result search(String query,int page)throws Exception{
        if(query.length()>100||page<1||page>100)throw new IOException("Invalid request");
        String path="https://addons.mozilla.org/api/v5/addons/search/?app=android&type=extension&page_size=20&page="+page+"&lang=ru";
        if(query.isEmpty())path+="&sort=users";else path+="&q="+URLEncoder.encode(query,"UTF-8");
        HttpURLConnection conn=(HttpURLConnection)new URL(path).openConnection();
        conn.setConnectTimeout(12000);conn.setReadTimeout(15000);conn.setInstanceFollowRedirects(false);conn.setRequestProperty("Accept","application/json");
        try{
            if(conn.getResponseCode()!=200)throw new IOException("HTTP "+conn.getResponseCode());
            byte[] bytes;try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>2*1024*1024)throw new IOException("Response too large");out.write(buf,0,n);}bytes=out.toByteArray();
            }
            JSONObject root=new JSONObject(new String(bytes,StandardCharsets.UTF_8));JSONArray rows=root.getJSONArray("results");List<Addon> list=new ArrayList<>();
            for(int i=0;i<rows.length()&&i<20;i++){JSONObject a=rows.optJSONObject(i);if(a==null||a.optBoolean("is_disabled"))continue;JSONObject version=a.optJSONObject("current_version");if(version==null)continue;JSONObject file=version.optJSONObject("file");if(file==null)continue;
                String url=file.optString("url","");if(!official(url))continue;
                String name=localized(a.opt("name"));if(name.isEmpty())continue;
                String summary=localized(a.opt("summary"));if(summary.length()>500)summary=summary.substring(0,500)+"…";
                String site=a.optString("url","");list.add(new Addon(name,summary,version.optString("version",""),url,official(site)?site:""));
            }
            return new Result(list,root.optInt("count",list.size()),!root.isNull("next")&&!root.optString("next","").isEmpty());
        }finally{conn.disconnect();}
    }
}
