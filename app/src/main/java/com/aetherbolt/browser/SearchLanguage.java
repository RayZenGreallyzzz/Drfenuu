package com.aetherbolt.browser;
public final class SearchLanguage {
    private SearchLanguage(){}
    public static String template(String template,String language){
        if(!"uk".equals(language)&&!"ru".equals(language)&&!"en".equals(language))return template;
        if(Router.ENGINES[0][1].equals(template))return template+"&lr=lang_"+language;
        if(Router.ENGINES[1][1].equals(template))return template+"&kl="+("uk".equals(language)?"ua-uk":"ru".equals(language)?"ru-ru":"us-en");
        if(Router.ENGINES[3][1].equals(template))return template.replace("{query}","{query}+lang%3A"+language);
        return template;
    }
    public static String support(String template){
        if(Router.ENGINES[0][1].equals(template)||Router.ENGINES[3][1].equals(template))return "Языковое ограничение (решает поисковик)";
        if(Router.ENGINES[1][1].equals(template))return "Региональное предпочтение, не строгий фильтр";
        return "Для этого поисковика фильтр пока не подключён";
    }
}
