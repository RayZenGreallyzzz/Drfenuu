package com.aetherbolt.browser;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.TrafficStats;
import android.os.BatteryManager;
import android.os.Debug;
import android.os.Process;
import android.text.format.Formatter;
import java.util.Locale;

/** Snapshot only, no background sampling or privileged APIs. */
final class PerformanceSnapshot {
    private PerformanceSnapshot(){}
    static String read(Context c){
        StringBuilder out=new StringBuilder();
        try{
            ActivityManager am=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            out.append("ОЗУ устройства: ").append(Formatter.formatShortFileSize(c,Math.max(0,info.totalMem-info.availMem))).append(" / ").append(Formatter.formatShortFileSize(c,info.totalMem));
            out.append("\nДоступно Android: ").append(Formatter.formatShortFileSize(c,info.availMem));
            if(info.lowMemory)out.append(" • низкий запас памяти");
            Debug.MemoryInfo mem=new Debug.MemoryInfo();Debug.getMemoryInfo(mem);
            out.append("\nPSS текущего процесса: ").append(Formatter.formatShortFileSize(c,mem.getTotalPss()*1024L));
            out.append(" (не все процессы GeckoView)");
        }catch(RuntimeException e){out.append("Память: данные недоступны");}
        try{
            Intent battery=c.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if(battery!=null){
                int level=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,-1),temp=battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Integer.MIN_VALUE);
                out.append("\n\nБатарея: ").append(level>=0&&scale>0?Math.round(100f*level/scale)+"%":"нет данных");
                if(temp!=Integer.MIN_VALUE)out.append("\nТемпература батареи: ").append(String.format(Locale.ROOT,"%.1f °C",temp/10f));
                out.append("\nПитание: ").append(battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)==0?"от батареи":"подключено");
            }
        }catch(RuntimeException e){out.append("\n\nБатарея: данные недоступны");}
        try{
            long rx=TrafficStats.getUidRxBytes(Process.myUid()),tx=TrafficStats.getUidTxBytes(Process.myUid());
            out.append("\n\nТрафик UID с запуска Android:");
            out.append("\nПолучено: ").append(rx>=0?Formatter.formatShortFileSize(c,rx):"нет данных");
            out.append("\nОтправлено: ").append(tx>=0?Formatter.formatShortFileSize(c,tx):"нет данных");
            out.append("\nНе включает весь возможный трафик отдельных изолированных процессов/VPN.");
        }catch(RuntimeException e){out.append("\n\nТрафик: данные недоступны");}
        return out.toString();
    }
}
