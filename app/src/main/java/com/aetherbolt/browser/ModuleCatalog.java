package com.aetherbolt.browser;
import android.content.Context;
import android.content.SharedPreferences;
import android.app.Activity;
import android.widget.Toast;

/** Availability gates for optional local tools, not a network security switch. */
final class ModuleCatalog {
    static final String[][] LOCAL={
        {"media","Media Center Pro","LibVLC • локальное видео и аудио"},
        {"files","File Center","SAF • управление выбранными папками"},
        {"documents","Document Center","PDF • текст • HEX • ZIP/GZIP"},
        {"tor","Tor Engine","Нативный Tor • отдельный текстовый экран"},
        {"eruda","Eruda","Инспектор текущей страницы"},
        {"xterm","xterm","Консоль текущей страницы"},
        {"cyberchef","CyberChef","Локальное преобразование данных"},
        {"v86","Linux v86","Локальная виртуальная машина"}
    };
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("aetherbolt",Context.MODE_PRIVATE);}
    static boolean enabled(Context c,String id){return prefs(c).getBoolean("module."+id,true);}
    static void set(Context c,String id,boolean on){prefs(c).edit().putBoolean("module."+id,on).apply();}
    static void reset(Context c){SharedPreferences.Editor e=prefs(c).edit();for(String[] m:LOCAL)e.remove("module."+m[0]);e.apply();}
    static boolean guard(Activity a,String id){if(enabled(a,id))return true;Toast.makeText(a,"Модуль отключён. Настройки → Module Manager",Toast.LENGTH_LONG).show();return false;}
    static String toolId(String page){if(page==null)return "";if(page.startsWith("cyberchef/"))return "cyberchef";if(page.startsWith("v86/"))return "v86";return "";}
}
