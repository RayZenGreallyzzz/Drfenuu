package com.aetherbolt.browser;

import android.graphics.*;
import android.graphics.drawable.Drawable;

final class BoltIcon extends Drawable {
    private final String key; private final int color; private final Paint p=new Paint(3);
    BoltIcon(String key,int color){this.key=key;this.color=color;}
    static boolean supports(String k){return "⌂|‹|›|↻|☆|▣|☰|×|+|＋|⌕|⚙|◷|↓|✧|◈|◎|☾|▤|◉|▧|▶|⚡|✓|↗|ⓘ|🧩".contains("|"+k+"|")||k.equals("⌂")||k.equals("ⓘ");}
    private void line(Canvas c,float... coords){Path a=new Path();a.moveTo(coords[0],coords[1]);for(int i=2;i<coords.length;i+=2)a.lineTo(coords[i],coords[i+1]);c.drawPath(a,p);}
    private void round(Canvas c,float l,float t,float r,float b,float rad){c.drawRoundRect(l,t,r,b,rad,rad,p);}
    @Override public void draw(Canvas c){Rect b=getBounds();float scale=Math.min(b.width(),b.height())/24f;c.save();c.translate(b.left+(b.width()-24*scale)/2,b.top+(b.height()-24*scale)/2);c.scale(scale,scale);
      p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.65f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
      switch(key){
       case "🧩": {Path a=new Path();a.moveTo(4,5);a.lineTo(9,5);a.cubicTo(7,0,17,0,15,5);a.lineTo(20,5);a.lineTo(20,10);a.cubicTo(25,8,25,18,20,16);a.lineTo(20,21);a.lineTo(14,21);a.cubicTo(16,16,6,16,8,21);a.lineTo(4,21);a.lineTo(4,15);a.cubicTo(9,17,9,7,4,9);a.close();c.drawPath(a,p);break;}
       case "⌂": line(c,3,11,12,3,21,11);line(c,5,10,5,21,10,21,10,15,14,15,14,21,19,21,19,10);break;
       case "‹": line(c,15,5,8,12,15,19);break;
       case "›": line(c,9,5,16,12,9,19);break;
       case "↻": c.drawArc(4,4,20,20,35,290,false,p);line(c,20,3,20,10,14,10);break;
       case "☆": {Path a=new Path();for(int i=0;i<10;i++){double t=-Math.PI/2+i*Math.PI/5;float r=i%2==0?9:4.3f;float x=12+(float)Math.cos(t)*r,y=12+(float)Math.sin(t)*r;if(i==0)a.moveTo(x,y);else a.lineTo(x,y);}a.close();c.drawPath(a,p);break;}
       case "▣": round(c,7,3,21,17,3);round(c,3,7,17,21,3);break;
       case "☰": line(c,4,6,20,6);line(c,4,12,20,12);line(c,4,18,20,18);break;
       case "×": line(c,6,6,18,18);line(c,18,6,6,18);break;
       case "+": case "＋": line(c,12,5,12,19);line(c,5,12,19,12);break;
       case "⌕": c.drawCircle(10,10,6,p);line(c,14.5f,14.5f,21,21);break;
       case "⚙": {Path a=new Path();for(int i=0;i<32;i++){double t=i*Math.PI/16;float r=i%4<2?10:8;float x=12+(float)Math.cos(t)*r,y=12+(float)Math.sin(t)*r;if(i==0)a.moveTo(x,y);else a.lineTo(x,y);}a.close();c.drawPath(a,p);c.drawCircle(12,12,3.3f,p);break;}
       case "◷": c.drawCircle(12,12,9,p);line(c,12,6,12,12,16,15);break;
       case "↓": line(c,12,3,12,15);line(c,7,10,12,15,17,10);line(c,4,17,4,21,20,21,20,17);break;
       case "✧": {Path a=new Path();a.moveTo(12,2);a.quadTo(13,10,22,12);a.quadTo(13,14,12,22);a.quadTo(10,14,2,12);a.quadTo(10,10,12,2);c.drawPath(a,p);break;}
       case "◈": {Path a=new Path();a.moveTo(12,2);a.lineTo(21,6);a.lineTo(20,13);a.quadTo(18,19,12,22);a.quadTo(6,19,4,13);a.lineTo(3,6);a.close();c.drawPath(a,p);line(c,8,12,11,15,16,9);break;}
       case "◎": c.drawCircle(12,12,9,p);c.drawOval(8,3,16,21,p);line(c,3,12,21,12);break;
       case "☾": {Path a=new Path();a.moveTo(14,3);a.cubicTo(3,0,0,17,10,21);a.cubicTo(16,23,20,18,21,15);a.cubicTo(10,20,6,8,14,3);c.drawPath(a,p);break;}
       case "▤": round(c,4,3,20,21,3);for(int i=7;i<19;i+=4)line(c,8,i,16,i);break;
       case "◉": c.drawCircle(12,12,9,p);c.drawCircle(12,12,4,p);break;
       case "▧": round(c,3,3,21,21,3);c.drawCircle(8,8,2,p);line(c,4,18,10,12,14,16,18,10,21,13);break;
       case "▶": line(c,7,4,20,12,7,20,7,4);break;
       case "⚡": {Path a=new Path();a.moveTo(15,1);a.lineTo(4,14);a.lineTo(11,13);a.lineTo(8,23);a.lineTo(21,9);a.lineTo(13,10);a.close();p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(0,0,20,24,new int[]{0xff53ddfb,0xffa875ff},null,Shader.TileMode.CLAMP));c.drawPath(a,p);p.setShader(null);break;}
       case "✓": line(c,5,12,10,17,19,7);break;
       case "↗": line(c,6,18,18,6,10,6);line(c,18,6,18,14);break;
       case "ⓘ": c.drawCircle(12,12,9,p);line(c,12,11,12,17);c.drawCircle(12,7,0.7f,p);break;
       default: p.setStyle(Paint.Style.FILL);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));p.setTextSize(key.length()>1?11:19);p.setTextAlign(Paint.Align.CENTER);c.drawText(key,12,12-(p.ascent()+p.descent())/2,p);
      }c.restore();}
    @Override public void setAlpha(int alpha){p.setAlpha(alpha);}
    @Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
