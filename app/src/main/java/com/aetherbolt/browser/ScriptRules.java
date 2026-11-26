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
    public static String validate(String code){
        if(code.trim().isEmpty())return "Пустой скрипт";
        if(code.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>LIMIT)return "Лимит 256 КБ";
        if(Pattern.compile("(?m)^\\s*//\\s*@(require|resource|grant)\\s+(?!none\\s*$)\\S").matcher(code).find())return "GM API, @require и @resource пока не поддерживаются";
        String run=metadata(code,"run-at");
        if(!run.isEmpty()&&!run.equals("document-end")&&!run.equals("document-idle"))return "Поддерживается запуск только после загрузки страницы";
        if(Pattern.compile("(?m)^\\s*//\\s*@(match|include|exclude|exclude-match)\\s+").matcher(code).find())return "Метаданные @match/include/exclude пока не поддерживаются; нужен простой JS с ручным выбором сайта";
        return "";
    }
}
