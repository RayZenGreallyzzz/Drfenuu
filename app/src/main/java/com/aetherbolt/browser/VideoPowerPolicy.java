package com.aetherbolt.browser;

import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

/** Advisory display mode preference: never change resolution or global settings. */
final class VideoPowerPolicy {
    private boolean applied;
    private float previousRate;
    private int previousMode;
    void apply(Window window,boolean enable){
        if(enable==applied)return;
        WindowManager.LayoutParams p=window.getAttributes();
        if(enable){
            Display display=window.getDecorView().getDisplay();
            if(display==null)return;
            Display.Mode current=display.getMode(),candidate=null;
            for(Display.Mode mode:display.getSupportedModes()){
                if(mode.getPhysicalWidth()!=current.getPhysicalWidth()||mode.getPhysicalHeight()!=current.getPhysicalHeight())continue;
                if(Math.abs(mode.getRefreshRate()-60f)>1f)continue;
                if(candidate==null||Math.abs(mode.getRefreshRate()-60f)<Math.abs(candidate.getRefreshRate()-60f))candidate=mode;
            }
            if(candidate==null)return;
            previousRate=p.preferredRefreshRate;previousMode=p.preferredDisplayModeId;
            p.preferredDisplayModeId=0;
            p.preferredRefreshRate=candidate.getRefreshRate();applied=true;
        }else{
            p.preferredRefreshRate=previousRate;p.preferredDisplayModeId=previousMode;applied=false;
        }
        window.setAttributes(p);
    }
}
