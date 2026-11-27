package com.aetherbolt.browser;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

final class AetherStartupView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Bitmap monster;
    private final RectF art=new RectF();
    private final long started=SystemClock.uptimeMillis();
    private int stage;
    private String status="Запуск…";
    private final int[] colors={0xffde76ff,0xff49e9ff,0xfff0f4ff};
    AetherStartupView(Context c){
        super(c);monster=BitmapFactory.decodeResource(getResources(),R.drawable.aether_monster);
        setClickable(true);setFocusable(true);setContentDescription("Запуск AetherBolt");
        setLayerType(View.LAYER_TYPE_NONE,null);
    }
    void stage(int value,String text){stage=value;status=text;setContentDescription("AetherBolt. "+text);invalidate();}
    private void text(Canvas c,String text,float x,float y,float size,int color,boolean bold){
        p.setShader(null);p.setColor(color);p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(size);
        p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD_ITALIC:Typeface.NORMAL));c.drawText(text,x,y,p);
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        final float w=getWidth(),h=getHeight(),cx=w/2f;
        if(w<=0||h<=0)return;
        float unit=Math.min(w/440f,h/620f);
        p.setStyle(Paint.Style.FILL);p.setShader(new RadialGradient(cx,h*.34f,Math.max(w,h)*.68f,new int[]{0xff25114e,0xff0c1535,0xff050714},null,Shader.TileMode.CLAMP));
        c.drawRect(0,0,w,h,p);p.setShader(null);
        p.setColor(0xff9ac5ff);
        for(int i=0;i<84;i++){
            float sx=((i*137+53)%997)/997f*w,sy=((i*293+11)%991)/991f*h;
            p.setAlpha(50+(i*31)%140);c.drawCircle(sx,sy,unit*(i%7==0?1.5f:.65f),p);
        }
        p.setAlpha(255);
        float ah=Math.min(h*.56f,w*.85f),aw=ah*monster.getWidth()/monster.getHeight(),cy=h*.36f;
        art.set(cx-aw/2,cy-ah/2,cx+aw/2,cy+ah/2);
        int save=c.save();Path clip=new Path();clip.addRoundRect(art,20*unit,20*unit,Path.Direction.CW);c.clipPath(clip);
        c.drawBitmap(monster,null,art,p);c.restoreToCount(save);
        // Each comet is a moving head with a tapered, fading tail on its own elliptical orbit.
        double elapsed=(SystemClock.uptimeMillis()-started)/3000.0;
        float rx=Math.min(w*.43f,aw*.68f),ry=ah*.32f;
        for(int i=0;i<3;i++){
            double angle=elapsed*Math.PI*2+i*Math.PI*2/3;
            float tilt=(i-1)*18f;
            c.save();c.rotate(tilt,cx,cy);
            float orbitX=rx*(1f-i*.05f),orbitY=ry*(1f+i*.1f);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(unit*.8f);p.setColor(colors[i]);p.setAlpha(42);
            c.drawOval(cx-orbitX,cy-orbitY,cx+orbitX,cy+orbitY,p);
            p.setStyle(Paint.Style.FILL);
            for(int j=21;j>=1;j--){
                double a=angle-j*.047;
                float x=cx+(float)Math.cos(a)*orbitX,y=cy+(float)Math.sin(a)*orbitY;
                p.setColor(colors[i]);p.setAlpha((int)(200*(1-j/22f)));
                c.drawCircle(x,y,unit*(1f+4.8f*(1-j/22f)),p);
            }
            float x=cx+(float)Math.cos(angle)*orbitX,y=cy+(float)Math.sin(angle)*orbitY;
            p.setAlpha(255);p.setShader(new RadialGradient(x,y,unit*13,new int[]{0xffffffff,colors[i],colors[i]&0x00ffffff},new float[]{0,.35f,1},Shader.TileMode.CLAMP));
            c.drawCircle(x,y,unit*13,p);p.setShader(null);p.setColor(Color.WHITE);c.drawCircle(x,y,unit*3,p);
            c.restore();
        }
        text(c,"AetherBolt",cx,h*.735f,40*unit,0xfff4edff,true);
        text(c,"B e y o n d   t h e   L i m i t s",cx,h*.782f,13*unit,0xffc0b4f9,false);
        float width=Math.min(w*.7f,320*unit),left=cx-width/2,y=h*.84f;
        p.setColor(0xff262346);c.drawRoundRect(left,y,left+width,y+7*unit,5*unit,5*unit,p);
        float fraction=Math.max(0,Math.min(3,stage))/3f;
        if(fraction>0){
            p.setShader(new LinearGradient(left,y,left+width,y,new int[]{0xff8b5cf6,0xff57dfff},null,Shader.TileMode.CLAMP));
            c.drawRoundRect(left,y,left+width*fraction,y+7*unit,5*unit,5*unit,p);p.setShader(null);
        }
        text(c,status,cx,h*.897f,13*unit,0xffe7eaff,false);
        if(isAttachedToWindow()&&isShown())postInvalidateOnAnimation();
    }
}
