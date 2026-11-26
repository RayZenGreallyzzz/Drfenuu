package com.aetherbolt.browser;

import java.net.URI;
import java.util.Locale;
import java.util.regex.*;

public final class ScriptRules {
    public static final int LIMIT=256*1024;
    private ScriptRules(){}
    public static String origin(String raw){
        try{
            URI u=new URI(raw.trim());
            String scheme=u.getScheme(),host=u.getHost();
            if(scheme==null||host==null||u.getUserInfo()!=null||Router.onionHost(host))return "";
            scheme=scheme.toLowerCase(Locale.ROOT);host=host.toLowerCase(Locale.ROOT);
            if(!scheme.equals("https")&&!scheme.equals("http"))return "";
            int p=u.getPort();if(p>65535||p==0)return "";
            return scheme+"://"+host+((p<0||(scheme.equals("https")&&p==443)||(scheme.equals("http")&&p==80))?"":":"+p);
        }catch(Exception e){return "";}
    }
    public static String scope(String raw){
        String r=raw.trim();String o=origin(r);
        if(o.isEmpty())return "";
        try{URI u=new URI(r);String p=u.getRawPath();return (p==null||p.isEmpty()||p.equals("/"))&&u.getRawQuery()==null&&u.getRawFragment()==null?o:"";}catch(Exception e){return "";}
    }
    public static String metadata(String code,String key){
        Matcher m=Pattern.compile("(?m)^\\s*//\\s*@"+Pattern.quote(key)+"\\s+([^\\r\\n]+)").matcher(code);
        return m.find()?m.group(1).trim():"";
    }

    private static boolean glob(String pattern,String value){StringBuilder regex=new StringBuilder("^");for(String p:pattern.split("\\*",-1)){if(regex.length()>1)regex.append(".*");regex.append(Pattern.quote(p));}return Pattern.compile(regex.append("$").toString()).matcher(value).matches();}
    private static boolean matchPattern(String pattern,String url){
        if(pattern.equals("<all_urls>"))return !origin(url).isEmpty();
        Matcher m=Pattern.compile("^(\\*|https?|http)://([^/]+)(/.*)$").matcher(pattern);
        if(!m.matches())return false;
        try{URI u=new URI(url);String host=u.getHost().toLowerCase(Locale.ROOT),scheme=u.getScheme().toLowerCase(Locale.ROOT),expected=m.group(2).toLowerCase(Locale.ROOT);if(!m.group(1).equals("*")&&!scheme.equals(m.group(1)))return false;
            if(!expected.equals("*")&&!host.equals(expected)&&!(expected.startsWith("*.")&&(host.equals(expected.substring(2))||host.endsWith("."+expected.substring(2)))))return false;
            String path=u.getRawPath();if(path==null||path.isEmpty())path="/";if(u.getRawQuery()!=null)path+="?"+u.getRawQuery();return glob(m.group(3),path);
        }catch(Exception e){return false;}
    }
    public static boolean permits(String code,String url){if(origin(url).isEmpty())return false;Matcher m=Pattern.compile("(?m)^\\s*//\\s*@(match|include|exclude|exclude-match)\\s+([^\\r\\n]+)").matcher(code);boolean positive=false,allowed=false;while(m.find()){String type=m.group(1),pattern=m.group(2).trim();boolean hit=type.endsWith("match")?matchPattern(pattern,url):glob(pattern,url);if(type.startsWith("exclude")){if(hit)return false;}else{positive=true;allowed|=hit;}}return !positive||allowed;}
    public static String validate(String code){
        if(code.trim().isEmpty())return "Пустой скрипт";
        if(code.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>LIMIT)return "Лимит 256 КБ";
        if(Pattern.compile("(?m)^\\s*//\\s*@(require|resource|grant)\\s+(?!none\\s*$)\\S").matcher(code).find())return "GM API, @require и @resource пока не поддерживаются";
        String run=metadata(code,"run-at");
        if(!run.isEmpty()&&!run.equals("document-end")&&!run.equals("document-idle"))return "Поддерживается запуск только после загрузки страницы";
        Matcher scope=Pattern.compile("(?m)^\\s*//\\s*@(match|include|exclude|exclude-match)\\s+([^\\r\\n]+)").matcher(code);
        while(scope.find()){String type=scope.group(1),pattern=scope.group(2).trim();if(type.endsWith("match")&&!pattern.equals("<all_urls>")&&!pattern.matches("^(\\*|https?)://(\\*|\\*\\.[a-zA-Z0-9.-]+|[a-zA-Z0-9.-]+)(/.*)$"))return "Неподдерживаемый @match";if(!type.endsWith("match")&&pattern.startsWith("/"))return "Регулярные выражения @include/exclude не поддерживаются";}
        return "";
    }
}
