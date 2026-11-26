package com.aetherbolt.browser;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import org.videolan.libvlc.*;
import org.videolan.libvlc.util.VLCVideoLayout;
import java.util.*;

public final class MediaCenterActivity extends Activity {
    private static final int SELECT=910, TEXT=0xffedf2ff,CYAN=0xff53ddfb;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LibVLC lib;
    private MediaPlayer player;
    private ParcelFileDescriptor file;
    private VLCVideoLayout video;
    private LinearLayout top,controls;
    private TextView title,status,play,time;
    private SeekBar seek;
    private Uri current;
    private String currentName="Медиацентр";
    private boolean dragging,closed;
    private final Runnable tick=new Runnable(){public void run(){
        if(closed)return;
        if(player!=null)try{
            long length=player.getLength(),pos=Math.max(0,player.getTime());
            if(!dragging)seek.setProgress(length>0?(int)Math.min(1000,1000.0*pos/length):0);
            seek.setEnabled(length>0&&player.isSeekable());
            time.setText(formatTime(pos)+" / "+(length>0?formatTime(length):"—"));
        }catch(RuntimeException ignored){}
        handler.postDelayed(this,500);
    }};
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    static boolean isMedia(String n,String mime){
        if(mime!=null&&(mime.startsWith("video/")||mime.startsWith("audio/")))return true;
        return n!=null&&n.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|m4v|mkv|webm|avi|mov|qt|wmv|asf|flv|f4v|mpg|mpeg|vob|ts|mts|m2ts|3gp|3g2|ogv|rm|rmvb|divx|mp3|m4a|aac|adts|wav|wave|flac|ogg|oga|opus|wma|aiff|aif|ape|alac|ac3|eac3|dts|mka|amr|mid|midi|au|caf)$");
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xff000000);
        video=new VLCVideoLayout(this);root.addView(video,new FrameLayout.LayoutParams(-1,-1));
        View tap=new View(this);root.addView(tap,new FrameLayout.LayoutParams(-1,-1));tap.setOnClickListener(v->showControls(top.getVisibility()!=View.VISIBLE));
        top=new LinearLayout(this);top.setOrientation(LinearLayout.VERTICAL);top.setPadding(dp(12),dp(8),dp(12),dp(8));top.setBackgroundColor(0xd90b1020);
        LinearLayout head=row();head.addView(button("‹",()->finish()),new LinearLayout.LayoutParams(dp(52),dp(48)));
        title=text("Медиацентр",18);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);head.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));head.addView(button("Файл",this::pick),new LinearLayout.LayoutParams(dp(76),dp(48)));top.addView(head);
        status=text("Выберите видео или аудио",13);top.addView(status,new LinearLayout.LayoutParams(-1,-2));root.addView(top,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        controls=new LinearLayout(this);controls.setOrientation(LinearLayout.VERTICAL);controls.setBackgroundColor(0xd90b1020);controls.setPadding(dp(12),dp(6),dp(12),dp(12));
        time=text("00:00 / —",14);controls.addView(time);seek=new SeekBar(this);seek.setMax(1000);seek.setEnabled(false);seek.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN));controls.addView(seek,new LinearLayout.LayoutParams(-1,dp(42)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s){dragging=true;}
            public void onProgressChanged(SeekBar s,int progress,boolean fromUser){}
            public void onStopTrackingTouch(SeekBar s){dragging=false;if(player!=null&&player.isSeekable()&&player.getLength()>0)player.setTime((long)(player.getLength()*(s.getProgress()/1000.0)));}
        });
        LinearLayout buttons=row();addButton(buttons,"−10с",()->jump(-10000));play=button("▶",()->{if(player==null){pick();return;}if(player.isPlaying())player.pause();else player.play();});buttons.addView(play,new LinearLayout.LayoutParams(0,dp(48),1));addButton(buttons,"+10с",()->jump(10000));addButton(buttons,"Дорожки",this::tracks);addButton(buttons,"Скрыть",()->showControls(false));controls.addView(buttons);
        LinearLayout extra=row();addButton(extra,"Другой плеер",this::external);addButton(extra,"Повтор",()->{if(current!=null)open(current,currentName);});controls.addView(extra);
        root.addView(controls,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        setContentView(root);immersive();
        Uri supplied=getIntent().getData();if(supplied!=null&&"content".equals(supplied.getScheme()))open(supplied,getIntent().getStringExtra("title"));else pick();
        handler.post(tick);
    }
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setOrientation(LinearLayout.HORIZONTAL);return r;}
    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(TEXT);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private TextView button(String s,Runnable action){TextView b=text(s,14);b.setGravity(Gravity.CENTER);android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(0xff171e35);bg.setCornerRadius(dp(12));bg.setStroke(dp(1),0xff52618c);b.setBackground(bg);b.setOnClickListener(v->action.run());return b;}
    private void addButton(LinearLayout row,String label,Runnable action){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);p.setMargins(dp(2),dp(2),dp(2),dp(2));row.addView(button(label,action),p);}
    private void pick(){try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),SELECT);}catch(Exception e){status.setText("Не удалось открыть файлы");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==SELECT&&result==RESULT_OK&&data!=null&&data.getData()!=null)open(data.getData(),null);}
    private String displayName(Uri u){try(Cursor c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())return c.getString(0);}catch(Exception ignored){}return "Локальный файл";}
    private void open(Uri u,String name){
        if(!"content".equals(u.getScheme())){status.setText("Разрешены локальные файлы Android");return;}
        releasePlayer();current=u;currentName=name==null?displayName(u):name;title.setText(currentName);status.setText("Открытие…");showControls(true);
        try{
            file=getContentResolver().openFileDescriptor(u,"r");if(file==null)throw new java.io.IOException("Нет доступа к файлу");
            lib=new LibVLC(this,new ArrayList<String>());player=new MediaPlayer(lib);final MediaPlayer active=player;
            player.attachViews(video,null,true,false);
            player.setEventListener(event->runOnUiThread(()->{if(closed||player!=active)return;switch(event.type){
                case MediaPlayer.Event.Playing:play.setText("Ⅱ");status.setText("Воспроизведение");break;
                case MediaPlayer.Event.Paused:play.setText("▶");status.setText("Пауза");break;
                case MediaPlayer.Event.Buffering:status.setText("Буферизация");break;
                case MediaPlayer.Event.EndReached:play.setText("▶");status.setText("Воспроизведение завершено");showControls(true);break;
                case MediaPlayer.Event.EncounteredError:play.setText("▶");status.setText("Ошибка декодирования. Попробуйте другой проигрыватель.");showControls(true);break;
                default:break;
            }}));
            Media media=new Media(lib,file.getFileDescriptor());try{media.setHWDecoderEnabled(true,false);player.setMedia(media);}finally{media.release();}
            player.play();
        }catch(Exception|LinkageError e){releasePlayer();status.setText("Ошибка открытия: "+e.getMessage());}
    }
    private void tracks(){
        if(player==null)return;MediaPlayer p=player;
        new android.app.AlertDialog.Builder(this).setTitle("Дорожки").setItems(new String[]{"Аудио","Субтитры"},(d,n)->{
            if(player!=p)return;
            MediaPlayer.TrackDescription[] list=n==0?p.getAudioTracks():p.getSpuTracks();
            if(list==null||list.length==0){Toast.makeText(this,"Дорожек нет",Toast.LENGTH_SHORT).show();return;}
            String[] labels=new String[list.length];for(int i=0;i<list.length;i++)labels[i]=list[i].name;
            new android.app.AlertDialog.Builder(this).setTitle(n==0?"Аудио":"Субтитры").setItems(labels,(a,i)->{if(player==p){if(n==0)p.setAudioTrack(list[i].id);else p.setSpuTrack(list[i].id);}}).show();
        }).show();
    }
    private void jump(long delta){if(player!=null&&player.isSeekable()){long next=Math.max(0,player.getTime()+delta);if(player.getLength()>0)next=Math.min(next,player.getLength());player.setTime(next);}}
    private static String formatTime(long ms){long sec=Math.max(0,ms/1000);return sec>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",sec/3600,sec/60%60,sec%60):String.format(Locale.ROOT,"%02d:%02d",sec/60,sec%60);}
    private void external(){if(current==null)return;try{String m=getContentResolver().getType(current);Intent i=new Intent(Intent.ACTION_VIEW).setDataAndType(current,m==null?"*/*":m).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Открыть в другом проигрывателе"));}catch(Exception e){status.setText("Нет приложения для этого файла");}}
    private void showControls(boolean show){top.setVisibility(show?View.VISIBLE:View.GONE);controls.setVisibility(show?View.VISIBLE:View.GONE);immersive();}
    private void immersive(){if(Build.VERSION.SDK_INT>=30){WindowInsetsController c=getWindow().getInsetsController();if(c!=null){c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);c.hide(WindowInsets.Type.systemBars());}}else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
    @Override public void onWindowFocusChanged(boolean hasFocus){super.onWindowFocusChanged(hasFocus);if(hasFocus)immersive();}
    @Override protected void onStop(){if(player!=null&&player.isPlaying())player.pause();super.onStop();}
    private void releasePlayer(){if(player!=null){try{player.setEventListener(null);player.stop();player.detachViews();player.release();}catch(Exception ignored){}player=null;}if(lib!=null){try{lib.release();}catch(Exception ignored){}lib=null;}if(file!=null){try{file.close();}catch(Exception ignored){}file=null;}}
    @Override protected void onDestroy(){closed=true;handler.removeCallbacksAndMessages(null);releasePlayer();super.onDestroy();}
}
