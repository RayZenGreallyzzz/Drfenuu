package com.aetherbolt.browser;

import android.os.Build;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.view.Display;
import java.util.Locale;
import java.util.TreeSet;

/** Local capability enumeration only; does not instantiate or benchmark codecs. */
final class VideoDiagnostics {
    private VideoDiagnostics(){}
    static String displayInfo(Display d){
        if(d==null)return "Экран: недоступен";
        Display.Mode current=d.getMode();
        TreeSet<String> modes=new TreeSet<>();
        for(Display.Mode m:d.getSupportedModes()){
            if(m.getPhysicalWidth()==current.getPhysicalWidth()&&m.getPhysicalHeight()==current.getPhysicalHeight())
                modes.add(String.format(Locale.ROOT,"%.1f",m.getRefreshRate()));
        }
        return "Экран: "+current.getPhysicalWidth()+" × "+current.getPhysicalHeight()+"\nТекущая частота: "+String.format(Locale.ROOT,"%.1f",d.getRefreshRate())+" Гц\nЧастоты при этом разрешении: "+modes+" Гц";
    }
    static String report(String display){
        StringBuilder out=new StringBuilder();
        out.append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append("\nAndroid ").append(Build.VERSION.RELEASE).append(" • API ").append(Build.VERSION.SDK_INT);
        if(Build.VERSION.SDK_INT>=31)out.append("\nSoC: ").append(Build.SOC_MANUFACTURER).append(' ').append(Build.SOC_MODEL);
        out.append("\nЯдра, доступные процессу: ").append(Runtime.getRuntime().availableProcessors()).append("\n\n").append(display).append("\n\nДЕКОДЕРЫ ANDROID\n");
        try{
            MediaCodecInfo[] codecs=new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos();
            String[][] formats={{"H.264 / AVC","video/avc"},{"H.265 / HEVC","video/hevc"},{"VP9","video/x-vnd.on2.vp9"},{"AV1","video/av01"},{"VP8","video/x-vnd.on2.vp8"}};
            for(String[] f:formats){
                TreeSet<String> hw=new TreeSet<>(),sw=new TreeSet<>(),other=new TreeSet<>();
                for(MediaCodecInfo c:codecs){
                    if(c.isEncoder())continue;
                    for(String type:c.getSupportedTypes()){
                        if(!type.equalsIgnoreCase(f[1]))continue;
                        if(Build.VERSION.SDK_INT>=29){
                            if(c.isHardwareAccelerated())hw.add(c.getName());
                            else if(c.isSoftwareOnly())sw.add(c.getName());
                            else other.add(c.getName());
                        }else other.add(c.getName());
                        break;
                    }
                }
                out.append("\n").append(f[0]).append("\nHW: ").append(hw.isEmpty()?"не заявлен":String.join(", ",hw));
                if(!sw.isEmpty())out.append("\nSW: ").append(String.join(", ",sw));
                if(!other.isEmpty())out.append("\nНе определён: ").append(String.join(", ",other));
                out.append('\n');
            }
        }catch(RuntimeException e){out.append("\nСписок кодеков недоступен.\n");}
        out.append("\nHW — аппаратный, SW — программный. Это возможности, заявленные Android, а не измерение текущего воспроизведения. Выбранный сайтом кодек и фактический декодер GeckoView здесь не определяются.");
        return out.toString();
    }
}
