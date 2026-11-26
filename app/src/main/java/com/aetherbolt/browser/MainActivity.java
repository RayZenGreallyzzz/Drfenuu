package com.aetherbolt.browser;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.*;
import android.webkit.URLUtil;
import org.mozilla.geckoview.*;
import android.widget.*;
import java.io.ByteArrayInputStream;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {
    private static final int BG=0xff0b1020, PANEL=0xff171e35, TEXT=0xffedf2ff, MUTED=0xffa5b1ce, PURPLE=0xffa78bfa, CYAN=0xff53ddfb;
    private LinearLayout root, tabsBar, toolBar; private FrameLayout body; private EditText address;
    private HorizontalScrollView tabStrip;
    private LinearLayout bottomBar;
    private Tab fullscreenTab;
    private boolean fullscreenKeptScreenOn;
    private int previousSystemUi;
    private int previousBarsBehavior;
    private boolean fullscreenUi=false;
    private void setFullscreenUi(boolean enabled){
        if(fullscreenUi==enabled)return;
        fullscreenUi=enabled;
        Window window=getWindow();
        View decor=window.getDecorView();
        if(enabled){
            dismissPanel();address.clearFocus();
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);
            fullscreenKeptScreenOn=(window.getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0;
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }else if(!fullscreenKeptScreenOn)window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        toolBar.setVisibility(enabled?View.GONE:View.VISIBLE);
        bottomBar.setVisibility(enabled?View.GONE:View.VISIBLE);
        tabStrip.setVisibility(!enabled&&getResources().getConfiguration().screenWidthDp>=600?View.VISIBLE:View.GONE);
        if(enabled)progress.setVisibility(View.GONE);
        else if(!tabs.isEmpty())updateProgress(current());
        if(Build.VERSION.SDK_INT>=30){
            WindowInsetsController c=window.getInsetsController();
            if(c!=null){
                if(enabled){previousBarsBehavior=c.getSystemBarsBehavior();c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);c.hide(WindowInsets.Type.systemBars());}
                else{c.show(WindowInsets.Type.systemBars());c.setSystemBarsBehavior(previousBarsBehavior);}
            }
        }else{
            if(enabled){previousSystemUi=decor.getSystemUiVisibility();decor.setSystemUiVisibility(previousSystemUi|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
            else decor.setSystemUiVisibility(previousSystemUi);
        }
        shell.requestApplyInsets();
    }
    private void fullscreenChanged(Tab t,boolean enabled){
        if(isDestroyed()||isFinishing())return;
        if(enabled){
            if(tabs.isEmpty()||!tabs.contains(t)||current()!=t||t.home){if(t.session.isOpen())t.session.exitFullScreen();return;}
            if(fullscreenTab!=null&&fullscreenTab!=t)leaveFullscreen();
            fullscreenTab=t;setFullscreenUi(true);
        }else if(fullscreenTab==t){fullscreenTab=null;setFullscreenUi(false);}
    }
    private void leaveFullscreen(){
        Tab old=fullscreenTab;fullscreenTab=null;setFullscreenUi(false);
        if(old!=null&&old.session.isOpen())old.session.exitFullScreen();
    }
    @Override public void onWindowFocusChanged(boolean focused){
        super.onWindowFocusChanged(focused);
        if(!focused||!fullscreenUi)return;
        if(Build.VERSION.SDK_INT>=30){WindowInsetsController c=getWindow().getInsetsController();if(c!=null)c.hide(WindowInsets.Type.systemBars());}
        else getWindow().getDecorView().setSystemUiVisibility(previousSystemUi|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
    private final ArrayDeque<String> closedPages=new ArrayDeque<>();
    private DevSuiteFeature devSuite;
    private FileTransfers transfers;
    private final Set<Tab> capturing=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Tab,TextView> tabTitles=new IdentityHashMap<>();
    private final Handler chromeHandler=new Handler(Looper.getMainLooper());
    private boolean touchDown=false;
    private long quietAfter=0;
    private final Runnable titleUpdate=new Runnable(){public void run(){
        if(isDestroyed())return;
        if(touchDown||SystemClock.uptimeMillis()<quietAfter){chromeHandler.postDelayed(this,200);return;}
        for(Map.Entry<Tab,TextView> e:tabTitles.entrySet()){
            Tab t=e.getKey();TextView v=e.getValue();String title=t.home?"Новая вкладка":t.title;
            if(!android.text.TextUtils.equals(v.getText(),title))v.setText(title);
        }
    }};
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        // Observe only. Do not consume, copy, resample or redispatch Gecko input.
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:touchDown=true;break;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:touchDown=false;quietAfter=SystemClock.uptimeMillis()+900;break;
            default:break;
        }
        return super.dispatchTouchEvent(event);
    }
    private void scheduleTitleUpdate(){chromeHandler.removeCallbacks(titleUpdate);chromeHandler.postDelayed(titleUpdate,Math.max(100,quietAfter-SystemClock.uptimeMillis()));}
    private FrameLayout shell,overlay; private String panelTitle="";
    private static final class PanelState {final String title;final FrameLayout view;PanelState(String t,FrameLayout v){title=t;view=v;}}
    private final ArrayDeque<PanelState> panelHistory=new ArrayDeque<>(); private Tab shownTab; private static final int SCRIPT_PICK=702; private final ArrayList<UserScript> scripts=new ArrayList<>(); private static class UserScript {String id,name,site,code;boolean enabled; UserScript(String i,String n,String o,String c,boolean e){id=i;name=n;site=o;code=c;enabled=e;}} private TextView tabCount; private TextView downloadButton; private ProgressBar progress; private android.content.SharedPreferences prefs;
    private BoltVpn vpn; private static GeckoRuntime runtime; private WebExtensionController extensions; private WebExtension scriptBridge; private final ArrayList<Tab> tabs=new ArrayList<>(); private int active=0;
    private static class Tab { WebExtension extensionOwner; Tab opener; GeckoView web; GeckoSession session; String url="", pendingUrl=""; boolean canBack,canForward,webActive=true; boolean home=true; String title="Новая вкладка"; int progress=0; final HashSet<String> executed=new HashSet<>(); android.graphics.Bitmap preview, favicon; }
    private final ArrayList<String> bookmarks=new ArrayList<>(), history=new ArrayList<>();
    private String engineName="Google", engineTemplate=Router.ENGINES[0][1];
    private volatile boolean filter=true, tracker=true; private boolean js=true, images=true, desktop=false, historyEnabled=true, thirdParty=false, autoplay=false, darkPages=false, zoom=true; private String appearance="Космос"; private final ArrayList<String> downloads=new ArrayList<>();
    @Override public void onCreate(Bundle state) {
        setTheme(R.style.AppTheme);
        super.onCreate(state);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences("aetherbolt", MODE_PRIVATE);
        engineName=prefs.getString("engineName", "Google"); engineTemplate=prefs.getString("engineTemplate", Router.ENGINES[0][1]); filter=prefs.getBoolean("filter", true);
        readList("bookmarks",bookmarks); readList("history",history); readList("downloads",downloads); loadOptions(); loadScripts();
        if(runtime==null)runtime=GeckoRuntime.create(getApplicationContext()); extensions=runtime.getWebExtensionController(); vpn=BoltVpn.get(this);vpn.restore(err->{}); applyProtection(); transfers=new FileTransfers(this); build(); configureExtensions(); initScriptBridge(); devSuite=new DevSuiteFeature(this,runtime,()->tabs.isEmpty()||current().home?null:current().session,this::openLocalTool); devSuite.start(); restoreTabs();
    }
    private int dp(float x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private LinearLayout column(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);return c;}
    private TextView label(String text,int size,int color){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);return t;}
    private TextView button(String text,String desc,Runnable action){TextView t=label(text,17,TEXT);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(44));t.setMinWidth(dp(44));t.setPadding(dp(8),dp(6),dp(8),dp(6));t.setBackground(ripple(PANEL,15,0xff2a3352));if(BoltIcon.supports(text)){t.setText("");t.setCompoundDrawables(null,icon(text,TEXT,23),null,null);t.setPadding(dp(5),dp(5),dp(5),dp(5));}t.setContentDescription(desc);t.setOnClickListener(v->action.run());return t;}
    private void add(LinearLayout parent, View v,int width,int height){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height));p.setMargins(dp(3),dp(3),dp(3),dp(3));parent.addView(v,p);}
    private void build(){
        shell=new FrameLayout(this);root=column();root.setBackgroundColor(themeBg());shell.addView(root,new FrameLayout.LayoutParams(-1,-1));
        if(Build.VERSION.SDK_INT>=35) shell.setOnApplyWindowInsetsListener((v,i)->{android.graphics.Insets in=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());v.setPadding(in.left,in.top,in.right,in.bottom);return i;});
        tabStrip=new HorizontalScrollView(this);tabStrip.setHorizontalScrollBarEnabled(false);tabsBar=row();tabsBar.setPadding(dp(4),dp(3),dp(4),dp(3));tabStrip.addView(tabsBar);root.addView(tabStrip);tabStrip.setVisibility(getResources().getConfiguration().screenWidthDp>=600?View.VISIBLE:View.GONE);
        toolBar=row();toolBar.setPadding(dp(3),0,dp(3),0);
        add(toolBar,button("⌂","Главная",()->{current().home=true;showTab();}),44,44);
        address=new EditText(this);address.setSingleLine(true);address.setTextSize(15);address.setTextColor(TEXT);address.setHintTextColor(MUTED);address.setHint("Введите запрос или адрес");address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);address.setImeOptions(EditorInfo.IME_ACTION_GO);address.setSelectAllOnFocus(true);address.setPadding(dp(14),0,dp(14),0);address.setBackground(shape(PANEL,23,PURPLE));
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(46),1);a.setMargins(dp(3),0,dp(3),0);toolBar.addView(address,a);
        address.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_GO||(event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER&&event.getAction()==KeyEvent.ACTION_DOWN)){navigate(address.getText().toString());return true;}return false;});
        add(toolBar,button("↻","Обновить",()->{if(!current().home)current().session.reload();}),44,44);add(toolBar,button(">_","DevSuite",()->devSuitePanel()),44,44);root.addView(toolBar);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN));root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));progress.setVisibility(View.INVISIBLE);
        body=new FrameLayout(this);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        bottomBar=row();LinearLayout bottom=bottomBar;bottom.setPadding(dp(4),0,dp(4),dp(4));
        String[] icons={"‹","+","▣","🧩","↓","☰"}, desc={"Назад","Новая вкладка","Вкладки","Расширения","Загрузки","Меню"};
        Runnable[] actions={()->back(),()->addTab(),()->tabDialog(),()->quickAddons(),()->downloadPanel(),()->menu()};
        for(int i=0;i<icons.length;i++){TextView b=button(icons[i],desc[i],actions[i]);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.setMargins(dp(3),dp(3),dp(3),dp(3));bottom.addView(b,p);if(i==2)tabCount=b;if(i==4)downloadButton=b;}root.addView(bottom);setContentView(shell);transfers.setChanged(this::refreshDownloadBadge);
    }
    private Tab current(){return tabs.get(active);}
    private Tab createTab(){
        Tab t=new Tab();t.web=new GeckoView(this);t.session=new GeckoSession();configure(t);t.session.open(runtime);t.web.setSession(t.session);attachScriptBridge(t);if(devSuite!=null)devSuite.attach(t.session);tabs.add(t);return t;
    }
    private void addTab(){if(tabs.size()>=12){toast("Лимит: 12 вкладок");return;}createTab();active=tabs.size()-1;showTab();}
    private void closeTab(int index){if(fullscreenTab==tabs.get(index))leaveFullscreen();Tab t=tabs.remove(index);transfers.cancelUpload(t.session);if(historyEnabled&&!t.home&&t.extensionOwner==null&&Router.parse(t.url).kind==Router.Kind.WEB){closedPages.addFirst(t.url);while(closedPages.size()>20)closedPages.removeLast();}if(devSuite!=null)devSuite.detach(t.session);if(shownTab==t)shownTab=null;if(t.web.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)t.web.getParent()).removeView(t.web);t.web.releaseSession();t.session.close();if(tabs.isEmpty()){active=0;addTab();return;}if(index<active)active--;else if(active>=tabs.size())active=tabs.size()-1;showTab();}
    private void refreshTabs(){if(tabCount!=null){tabCount.setText(String.valueOf(tabs.size()));tabCount.setCompoundDrawablePadding(dp(5));tabCount.setCompoundDrawables(icon("▣",CYAN,20),null,null,null);}tabTitles.clear();tabsBar.removeAllViews();for(int i=0;i<tabs.size();i++){final int n=i;Tab t=tabs.get(i);LinearLayout chip=row();chip.setBackground(shape(i==active?0xff30304f:PANEL,12,i==active?PURPLE:0));TextView title=label(t.home?"Новая вкладка":t.title,13,TEXT);tabTitles.put(t,title);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setMaxWidth(dp(130));title.setPadding(dp(10),dp(9),dp(8),dp(9));title.setOnClickListener(v->{active=n;showTab();});if(t.favicon!=null&&!t.home){ImageView fav=new ImageView(this);fav.setImageBitmap(t.favicon);add(chip,fav,18,18);}else {ImageView fav=new ImageView(this);fav.setImageDrawable(icon(t.home?"⚡":"◎",CYAN,18));add(chip,fav,18,18);}chip.addView(title);ImageView x=new ImageView(this);x.setImageDrawable(icon("×",TEXT,17));x.setScaleType(ImageView.ScaleType.CENTER);x.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x337f8cff),null,shape(0xffffffff,20,0)));x.setContentDescription("Закрыть вкладку");x.setOnClickListener(v->closeTab(n));chip.addView(x,new LinearLayout.LayoutParams(dp(44),dp(44)));add(tabsBar,chip,-2,44);}add(tabsBar,button("+","Новая вкладка",()->addTab()),44,44);}
    private void showTab(){dismissPanel();Tab t=current();if(fullscreenTab!=null&&(fullscreenTab!=t||t.home))leaveFullscreen();for(Tab other:tabs){boolean shouldBeActive=other==t&&!t.home;if(other.webActive!=shouldBeActive){other.session.setActive(shouldBeActive);if(other.session.isOpen())extensions.setTabActive(other.session,shouldBeActive);other.webActive=shouldBeActive;}}shownTab=t;if(t.home){body.removeAllViews();address.setText("");body.addView(home(),new FrameLayout.LayoutParams(-1,-1));}else{if(t.web.getParent()!=body){body.removeAllViews();if(t.web.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)t.web.getParent()).removeView(t.web);body.addView(t.web,new FrameLayout.LayoutParams(-1,-1));}if(!android.text.TextUtils.equals(address.getText(),t.url))address.setText(t.url);}updateProgress(t);refreshTabs();address.clearFocus();if(!t.home){t.web.requestFocus();if(!t.pendingUrl.isEmpty()){String url=t.pendingUrl;t.pendingUrl="";t.session.load(new GeckoSession.Loader().uri(url));}}}
    private void updateProgress(Tab t){if(!tabs.isEmpty()&&t==current()){progress.setProgress(t.progress);progress.setVisibility(fullscreenUi?View.GONE:!t.home&&t.progress>0&&t.progress<100?View.VISIBLE:View.INVISIBLE);}}
    private View home(){
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackground(appearance.equals("Космос")?new SpaceArt():shape(themeBg(),0,0));LinearLayout box=column();box.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL);box.setPadding(dp(18),dp(26),dp(18),dp(30));sc.addView(box,new ScrollView.LayoutParams(-1,-2));
        ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.bolt);add(box,icon,88,88);
        TextView brand=label("AetherBolt Browser",30,TEXT);brand.setGravity(Gravity.CENTER);brand.setTypeface(Typeface.DEFAULT,Typeface.BOLD_ITALIC);android.text.SpannableString sp=new android.text.SpannableString("AetherBolt Browser");sp.setSpan(new android.text.style.ForegroundColorSpan(PURPLE),11,18,0);brand.setText(sp);add(box,brand,-1,-2);
        TextView slogan=label("Beyond the Limits.",15,MUTED);slogan.setTypeface(Typeface.DEFAULT,Typeface.ITALIC);slogan.setGravity(Gravity.CENTER);add(box,slogan,-1,-2);
        TextView search=button("⌕     Поиск в интернете", "Поиск",()->{address.requestFocus();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(address,InputMethodManager.SHOW_IMPLICIT);});search.setBackground(ripple(0xd0202547,25,PURPLE));LinearLayout.LayoutParams searchP=new LinearLayout.LayoutParams(-1,dp(53));searchP.setMargins(0,dp(22),0,dp(5));box.addView(search,searchP);
        TextView e=label(engineName+"  ▾",13,CYAN);e.setGravity(Gravity.CENTER);e.setOnClickListener(v->engineDialog());add(box,e,-1,36);
        String[] names={"Google","DuckDuckGo","Bing","Brave Search","Qwant","Яндекс","Ahmia","Добавить"};String[] icons={"G","D","b","B","Q","Я","◎","+"};int[] colors={0xff79aaff,0xffff9c50,CYAN,0xffff855d,PURPLE,0xffff7d8a,0xffc69cff,TEXT};Runnable[] actions=new Runnable[8];for(int i=0;i<6;i++){final int n=i;actions[i]=()->setEngine(Router.ENGINES[n][0],Router.ENGINES[n][1]);}actions[6]=()->setEngine(Router.ENGINES[10][0],Router.ENGINES[10][1]);actions[7]=()->customEngine();
        int cols=getResources().getConfiguration().screenWidthDp>=720?8:4;grid(box,names,icons,colors,actions,cols);
        grid(box,new String[]{"Дополнения","Загрузки","Закладки","История","Настройки","Вкладки"},new String[]{"✧","↓","☆","◷","⚙","▣"},new int[]{PURPLE,CYAN},new Runnable[]{()->quickAddons(),()->downloadPanel(),()->listDialog("Закладки",bookmarks,"bookmarks"),()->listDialog("История",history,"history"),()->settings(),()->tabDialog()},getResources().getConfiguration().screenWidthDp>=650?6:3);
        TextView note=label("GECKO ALPHA 0.7.8   •   Tor пока недоступен",11,MUTED);note.setGravity(Gravity.CENTER);add(box,note,-1,28);return sc;
    }

    private android.graphics.drawable.Drawable ripple(int color,int radius,int stroke){return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x337f8cff),shape(color,radius,stroke),null);}
    private void dismissPanel(){if(overlay!=null){shell.removeView(overlay);overlay=null;}panelTitle="";panelHistory.clear();}
    private void panelBack(){if(overlay!=null)shell.removeView(overlay);overlay=null;panelTitle="";if(!panelHistory.isEmpty()){PanelState p=panelHistory.removeLast();overlay=p.view;panelTitle=p.title;shell.addView(overlay,new FrameLayout.LayoutParams(-1,-1));}}
    private void suspendPanel(String title){if(overlay==null)return;shell.removeView(overlay);if(!title.equals(panelTitle)){while(panelHistory.size()>=5)panelHistory.removeFirst();panelHistory.addLast(new PanelState(panelTitle,overlay));}overlay=null;}
    private LinearLayout panel(String title,String subtitle,boolean sheet){
        suspendPanel(title);panelTitle=title;((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);address.clearFocus();
        overlay=new FrameLayout(this);overlay.setBackgroundColor(sheet?0xa0050916:BG);shell.addView(overlay,new FrameLayout.LayoutParams(-1,-1));overlay.setOnClickListener(v->panelBack());
        LinearLayout card=column();card.setClickable(true);card.setPadding(dp(16),dp(14),dp(16),dp(14));card.setBackground(sheet?glass(26,0xff6552a3):glass(0,0));
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,sheet?-2:-1,sheet?Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL:Gravity.CENTER);
        if(sheet){cp.width=Math.min(getResources().getDisplayMetrics().widthPixels-dp(20),dp(620));cp.bottomMargin=dp(12);if(title.equals("Расширения")){cp.gravity=Gravity.TOP|Gravity.END;cp.width=Math.min(cp.width,dp(420));cp.topMargin=toolBar.getBottom()+dp(4);cp.rightMargin=dp(8);cp.bottomMargin=0;}}
        overlay.addView(card,cp);if(!sheet){card.setPadding(dp(16),dp(14),dp(16),dp(14));}
        if(sheet){View grip=new View(this);grip.setBackground(shape(0xff53607c,3,0));LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(dp(42),dp(4));gp.gravity=Gravity.CENTER_HORIZONTAL;gp.bottomMargin=dp(12);card.addView(grip,gp);}
        LinearLayout head=row();add(head,button("‹","Назад",()->panelBack()),44,44);LinearLayout words=column();words.setPadding(dp(10),0,0,0);TextView name=label(title,23,TEXT);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);words.addView(name);if(!subtitle.isEmpty())words.addView(label(subtitle,12,MUTED));head.addView(words,new LinearLayout.LayoutParams(0,-2,1));ImageView mark=new ImageView(this);mark.setImageResource(R.drawable.bolt);add(head,mark,36,36);card.addView(head);View divider=new View(this);divider.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0x0053ddfb,0xff6655bd,0x0053ddfb}));card.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);LinearLayout content=column();content.setPadding(0,dp(12),0,dp(10));scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        card.addView(scroll,sheet?new LinearLayout.LayoutParams(-1,Math.min(dp(430),getResources().getDisplayMetrics().heightPixels/2)):new LinearLayout.LayoutParams(-1,0,1));
        card.setTranslationY(dp(14));card.setAlpha(.5f);card.animate().translationY(0).alpha(1).setDuration(190).start();return content;
    }
    private void section(LinearLayout c,String title){TextView t=label(title,12,PURPLE);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setPadding(dp(8),dp(15),0,dp(7));c.addView(t);}
    private TextView badge(String symbol,int color){TextView b=label("",20,color);b.setGravity(Gravity.CENTER);b.setCompoundDrawables(null,icon(symbol,color,25),null,null);b.setPadding(0,dp(7),0,0);b.setBackground(glass(13,0xff424673));return b;}
    private void item(LinearLayout c,String icon,String title,String sub,String tail,Runnable action){
        LinearLayout line=row();line.setPadding(dp(10),dp(8),dp(10),dp(8));line.setBackground(glassRipple(18,0xff343c64));add(line,badge(icon,CYAN),44,44);
        LinearLayout words=column();words.setPadding(dp(9),0,dp(6),0);TextView t=label(title,16,TEXT);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);words.addView(t);if(!sub.isEmpty())words.addView(label(sub,12,MUTED));line.addView(words,new LinearLayout.LayoutParams(0,-2,1));TextView end=label(tail,19,PURPLE);end.setPadding(dp(4),0,dp(4),0);line.addView(end);line.setOnClickListener(v->action.run());add(c,line,-1,-2);
    }
    private View tile(String icon,String title,int tint,Runnable action){LinearLayout c=column();c.setGravity(Gravity.CENTER);c.setPadding(dp(3),dp(8),dp(3),dp(8));c.setBackground(glassRipple(18,0xff383c66));add(c,badge(icon,tint),42,42);TextView t=label(title,11,TEXT);t.setGravity(Gravity.CENTER);t.setMaxLines(2);c.addView(t);c.setContentDescription(title);c.setOnClickListener(v->action.run());return c;}
    private void grid(LinearLayout parent,String[] names,String[] icons,int[] colors,Runnable[] actions,int cols){for(int i=0;i<names.length;i+=cols){LinearLayout r=row();for(int j=i;j<Math.min(i+cols,names.length);j++){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(88),1);p.setMargins(dp(4),dp(4),dp(4),dp(4));r.addView(tile(icons[j],names[j],colors[j%colors.length],actions[j]),p);}parent.addView(r,new LinearLayout.LayoutParams(-1,-2));}}
    private class SpaceArt extends android.graphics.drawable.Drawable {
        android.graphics.Paint p=new android.graphics.Paint(3);
        @Override public void draw(android.graphics.Canvas c){android.graphics.Rect b=getBounds();float w=b.width(),h=b.height();if(w<1||h<1)return;c.save();c.translate(b.left,b.top);
            p.setShader(new android.graphics.LinearGradient(0,0,w,h,new int[]{0xff080d21,0xff241548,0xff080d20},null,android.graphics.Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);
            p.setShader(new android.graphics.RadialGradient(w*.82f,h*.22f,Math.max(w,h)*.55f,new int[]{0xff263d9c,0xff14204a,0x00101028},null,android.graphics.Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);
            p.setShader(null);java.util.Random rand=new java.util.Random(24);for(int i=0;i<105;i++){p.setColor(i%5==0?0xaad1e9ff:0x558da0ea);c.drawCircle(rand.nextFloat()*w,rand.nextFloat()*h,dp(i%5==0?1.1f:.5f),p);}
            float x=w*.92f,y=h*.18f,r=w*.33f;p.setShader(new android.graphics.RadialGradient(x-r*.48f,y-r*.5f,r*1.7f,new int[]{0xff5ed9ff,0xff244aca,0xff101631,0xff090c20},new float[]{0,.22f,.52f,1},android.graphics.Shader.TileMode.CLAMP));c.drawCircle(x,y,r,p);p.setShader(null);p.setStyle(android.graphics.Paint.Style.STROKE);p.setColor(0xaa65b8ff);p.setStrokeWidth(dp(1.4f));c.drawCircle(x,y,r,p);p.setStyle(android.graphics.Paint.Style.FILL);
            for(int j=0;j<5;j++){android.graphics.Path q=new android.graphics.Path();float base=h*(.48f+j*.095f);q.moveTo(0,base);q.cubicTo(w*.22f,base-h*.19f,w*.48f,base+h*.19f,w,base-h*.06f);p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(dp(3-j*.4f));p.setShader(new android.graphics.LinearGradient(0,0,w,0,new int[]{0xff7147ff,0xfff16aff,0xff42d9ff},null,android.graphics.Shader.TileMode.CLAMP));p.setAlpha(145-j*20);c.drawPath(q,p);p.setAlpha(255);}
            p.setShader(null);p.setStyle(android.graphics.Paint.Style.FILL);c.restore();}
        @Override public void setAlpha(int a){} @Override public void setColorFilter(android.graphics.ColorFilter cf){} @Override public int getOpacity(){return android.graphics.PixelFormat.OPAQUE;}
    }
    private void notice(String title,String message){LinearLayout c=panel(title,"AetherBolt Browser",true);TextView t=label(message,15,MUTED);t.setPadding(dp(12),dp(12),dp(12),dp(15));c.addView(t);item(c,"✓","Понятно","","",()->panelBack());}
    private EditText field(String hint){EditText e=new EditText(this);e.setSingleLine(true);e.setTextColor(TEXT);e.setHintTextColor(MUTED);e.setTextSize(15);e.setHint(hint);e.setPadding(dp(14),dp(10),dp(14),dp(10));e.setBackground(shape(PANEL,15,0xff6552a3));return e;}


    private void loadOptions(){js=prefs.getBoolean("js",true);images=prefs.getBoolean("images",true);desktop=prefs.getBoolean("desktop",false);historyEnabled=prefs.getBoolean("historyEnabled",true);thirdParty=prefs.getBoolean("thirdParty",false);autoplay=prefs.getBoolean("autoplay",false);darkPages=prefs.getBoolean("darkPages",false);zoom=prefs.getBoolean("zoom",true);tracker=prefs.getBoolean("tracker",true);appearance=prefs.getString("appearance","Космос");}
    private int themeBg(){return appearance.equals("AMOLED")?0xff000000:appearance.equals("Графит")?0xff171a23:BG;}
    private void applyOptions(Tab t){t.session.getSettings().setUseTrackingProtection(tracker);t.session.getSettings().setUserAgentMode(desktop?GeckoSessionSettings.USER_AGENT_MODE_DESKTOP:GeckoSessionSettings.USER_AGENT_MODE_MOBILE);}

    private void toggle(String key,boolean value,Runnable reopen){prefs.edit().putBoolean(key,value).apply();loadOptions();applyProtection();for(Tab t:tabs)applyOptions(t);reopen.run();}

    private void option(LinearLayout c,String icon,String title,String sub,String key,boolean value,Runnable reopen){item(c,icon,title,sub,value?"● Вкл":"○ Выкл",()->toggle(key,!value,reopen));}
    private boolean isTrackerHost(String h){if(h==null)return false;h=h.toLowerCase(Locale.ROOT);for(String d:new String[]{"google-analytics.com","analytics.google.com","scorecardresearch.com","hotjar.com","segment.io","connect.facebook.net","matomo.cloud"})if(h.equals(d)||h.endsWith("."+d))return true;return false;}
    private void applyDark(GeckoView v){}


    private int storeGeneration=0;
    private void addonStore(String query,int page){
        int generation=++storeGeneration;
        LinearLayout c=panel("Mozilla Add-ons","Официальный каталог • Firefox для Android",false);
        EditText search=field("Название расширения");search.setText(query);search.setSingleLine(true);add(c,search,-1,54);
        Runnable find=()->{String q=search.getText().toString().trim();if(q.length()>100){search.setError("До 100 символов");return;}addonStore(q,1);};
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.setOnEditorActionListener((v,id,event)->{if(id==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){find.run();return true;}return false;});
        item(c,"⌕","Найти","Поиск Mozilla","›",find);
        item(c,"↗","Открыть сайт Mozilla","Веб-каталог в AetherBolt","›",()->openVpnSite("https://addons.mozilla.org/android/"));
        section(c,"РАСШИРЕНИЯ • СТРАНИЦА "+page);
        TextView status=label("Загрузка каталога…",13,MUTED);add(c,status,-1,44);
        new Thread(()->{try{
            AmoCatalog.Result result=AmoCatalog.search(query,page);
            runOnUiThread(()->{if(isDestroyed()||generation!=storeGeneration||!c.isAttachedToWindow())return;
                status.setText(result.items.isEmpty()?"Нет результатов для Android":"Найдено: "+result.count);
                for(AmoCatalog.Addon a:result.items)item(c,"✧",a.name,a.summary+" • "+a.version,"›",()->addonStoreDetail(a));
                if(page>1)item(c,"‹","Предыдущая страница","","›",()->addonStore(query,page-1));
                if(result.hasNext&&page<100)item(c,"›","Следующая страница","","›",()->addonStore(query,page+1));
            });
        }catch(Exception e){runOnUiThread(()->{if(isDestroyed()||generation!=storeGeneration||!c.isAttachedToWindow())return;status.setText("Каталог недоступен. Проверь сеть.");item(c,"↻","Повторить запрос","","›",()->addonStore(query,page));});}},"amo-catalog").start();
    }
    private void addonStoreDetail(AmoCatalog.Addon a){
        LinearLayout c=panel(a.name,"Mozilla Add-ons • "+a.version,false);
        TextView info=label(a.summary,14,TEXT);info.setPadding(dp(8),dp(8),dp(8),dp(8));c.addView(info);
        item(c,"↓","Установить","Подписанный XPI • подтверждение разрешений","›",()->confirmXpi(a.download));
        if(!a.page.isEmpty())item(c,"↗","Страница расширения","Официальная страница Mozilla","›",()->openVpnSite(a.page));
        item(c,"✧","Установленные расширения","","›",()->extensionList());
    }

    private boolean visibleAddon(WebExtension e){return !e.id.equals("scripts@aetherbolt.local")&&!e.id.equals(DevSuiteFeature.BRIDGE_ID)&&!e.id.equals(DevSuiteFeature.TOOLS_ID);}
    private void quickAddons(){
        LinearLayout c=panel("Расширения","Быстрый запуск",true);
        section(c,"УСТАНОВЛЕННЫЕ");
        LinearLayout installed=column();c.addView(installed);
        extensions.list().then(list->{runOnUiThread(()->{if(isDestroyed())return;installed.removeAllViews();int n=0;
            for(WebExtension e:list){if(!visibleAddon(e))continue;n++;if(!registeredAddons.containsKey(e.id))registerAddon(e);boolean enabled=e.metaData!=null&&e.metaData.enabled;
                LinearLayout row=row();row.setBackground(glassRipple(14,0xff343c64));TextView name=button(extName(e),extName(e),()->{if(enabled)launchAddon(e);else extensionDetail(e);});name.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);name.setTextSize(15);row.addView(name,new LinearLayout.LayoutParams(0,dp(52),1));add(row,button("⚙","Управление "+extName(e),()->extensionDetail(e)),44,44);add(installed,row,-1,-2);
            }
            if(n==0)item(installed,"✧","Нет расширений","Установи из магазина","",()->addonStore("",1));
        });return null;},err->{runOnUiThread(()->toast("Ошибка списка"));return null;});
        section(c,"ДОБАВИТЬ");item(c,"+","Магазин Mozilla","Поиск и установка","›",()->addonStore("",1));
        item(c,"⚙","Управление расширениями","XPI из файла / по ссылке • настройки","›",()->addonManage());
    }
    private void addonManage(){LinearLayout c=panel("Управление расширениями","Mozilla • XPI",false);
        item(c,"+","Из файла XPI","","›",()->pickLocal(703));item(c,"+","По HTTPS-ссылке","","›",()->extensionUrl());item(c,"↗","Сайт Mozilla Add-ons","","›",()->openVpnSite("https://addons.mozilla.org/android/"));item(c,"P","Proton VPN","Открыть или установить","›",()->openOrInstallProton());item(c,"⚙","Все расширения","Включить, отключить, удалить","›",()->extensionList());item(c,"JS","Скрипты и 3D модули","","›",()->plugins());}
    private void plugins(){LinearLayout c=panel("Дополнения","GeckoView • WebExtensions",false);section(c,"ЛИЧНЫЕ МОДУЛИ");item(c,"JS","Менеджер скриптов",scripts.size()+" сохранено • ZIP / JS","›",()->scriptPanel());item(c,"▣","3D Link Finder","Поиск прямых ссылок на модели","›",()->installFinder());item(c,"JS","Mixamo / Meshy / 3D Downloader","Хранение локальных исходников","›",()->localArchives());section(c,"РАСШИРЕНИЯ");item(c,"✧","Магазин Mozilla","Поиск и установка расширений для Android","›",()->addonStore("",1));item(c,"P","Установить Proton VPN","Официальное расширение Mozilla • XPI","›",()->installProtonAddon());item(c,"+","Установить XPI по ссылке","Подписанное расширение Mozilla","›",()->extensionUrl());item(c,"+","Установить XPI из файла","Подписанное расширение Mozilla","›",()->pickLocal(703));item(c,"✧","Установленные расширения","Включение, отключение, удаление","›",()->extensionList());item(c,"↗","Сайт Mozilla Add-ons","Открыть официальный магазин в новой вкладке","›",()->openVpnSite("https://addons.mozilla.org/android/"));}

    private void reader(){notice("Reader","Перенос Reader ещё не завершён.");}

    private void privacy(){LinearLayout c=panel("Конфиденциальность","Gecko Edition",false);option(c,"◈","Блокировать известные трекеры","Gecko: списки трекеров и cookies","tracker",tracker,()->privacy());option(c,"◷","Сохранять историю","История и восстановление вкладок","historyEnabled",historyEnabled,()->{if(!historyEnabled)saveTabs();privacy();});item(c,"×","Очистить историю","История AetherBolt","›",()->clearData());item(c,"◎","Tor","Не реализован","›",()->notice("Tor","Нет интеграции Tor. Не используй для анонимности."));}

    private void webOptions(){LinearLayout c=panel("Настройки сайтов","Gecko Edition",false);option(c,"▣","Версия для ПК","User-Agent","desktop",desktop,()->webOptions());item(c,"↻","Обновить страницу","","›",()->{dismissPanel();if(!current().home)current().session.reload();});}

    private void themes(){LinearLayout c=panel("Внешний вид","Фирменные варианты оформления",false);for(String theme:new String[]{"Космос","AMOLED","Графит"})item(c,"✧",theme,theme.equals("Космос")?"Планета и неоновые линии":theme.equals("AMOLED")?"Чёрный фон стартовой страницы":"Спокойный тёмный фон",appearance.equals(theme)?"●":"○",()->{appearance=theme;prefs.edit().putString("appearance",theme).apply();root.setBackgroundColor(themeBg());showTab();});}
    private void requestDownload(String url,String ua,String disposition,String mime){notice("Загрузка","Старый механизм загрузки не используется.");}

    private void refreshDownloadBadge(){
        if(isDestroyed()||downloadButton==null)return;
        int n=transfers.activeCount();downloadButton.setText(n>0?String.valueOf(n):"");downloadButton.setCompoundDrawablePadding(dp(3));downloadButton.setCompoundDrawables(n>0?icon("↓",CYAN,19):null,n==0?icon("↓",TEXT,23):null,null,null);downloadButton.setContentDescription(n>0?"Загрузки: "+n+" в процессе":"Загрузки");
    }
    private void downloadPanel(){
        LinearLayout c=panel("Загрузки","AetherBolt • Все скачанные файлы",false);
        EditText search=field("Поиск файлов по названию");add(c,search,-1,48);
        section(c,"ТЕКУЩИЕ ЗАГРУЗКИ");
        LinearLayout activeJobs=column();c.addView(activeJobs);
        section(c,"СКАЧАННЫЕ ФАЙЛЫ");
        LinearLayout completed=column();c.addView(completed);
        Runnable files=()->renderDownloaded(completed,search.getText().toString());
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence x,int a,int n,int f){}public void onTextChanged(CharSequence x,int a,int b,int n){files.run();}public void afterTextChanged(android.text.Editable x){}});
        item(c,"↻","Обновить список","","›",files);
        item(c,"▤","Открыть системную папку","Download / AetherBolt","›",()->{try{startActivity(new Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS));}catch(Exception e){toast("Откройте приложение Файлы → Download");}});
        files.run();
        final long[] generation={-1};
        Runnable tick=new Runnable(){public void run(){
            if(isDestroyed()||!c.isAttachedToWindow())return;
            renderDownloadJobs(activeJobs);
            long revision=transfers.revision();if(generation[0]!=revision){generation[0]=revision;files.run();}
            c.postDelayed(this,750);
        }};
        c.post(tick);c.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){c.removeCallbacks(tick);c.post(tick);}public void onViewDetachedFromWindow(View v){c.removeCallbacks(tick);}});
    }
    private void renderDownloadJobs(LinearLayout c){
        c.removeAllViews();List<FileTransfers.Job> jobs=transfers.jobs();
        if(jobs.isEmpty()){add(c,label("Нет активных загрузок",13,MUTED),-1,40);return;}
        for(FileTransfers.Job j:jobs){
            LinearLayout card=column();card.setPadding(dp(12),dp(8),dp(12),dp(8));card.setBackground(glass(15,0xff343c64));
            TextView title=label(j.name,15,TEXT);title.setMaxLines(2);card.addView(title);
            long bytes=j.bytes,total=j.total;String amount=android.text.format.Formatter.formatFileSize(this,bytes);String status=j.status;
            boolean busy=j.busy();int percent=total>0?(int)Math.min(100,100.0*bytes/total):0;
            add(card,label(busy?status+" • "+amount+(total>0?" / "+android.text.format.Formatter.formatFileSize(this,total)+" • "+percent+"%":""):status,12,busy?CYAN:MUTED),-1,-2);
            if(busy){ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setIndeterminate(total<=0);bar.setMax(100);if(total>0)bar.setProgress(percent);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN));add(card,bar,-1,8);add(card,button("Отменить","Отменить скачивание",()->transfers.cancel(j)),-1,44);}else add(card,button("Убрать","Убрать сообщение",()->{transfers.dismiss(j);renderDownloadJobs(c);}),-1,44);
            add(c,card,-1,-2);
        }
    }
    private void renderDownloaded(LinearLayout c,String query){
        c.removeAllViews();String q=query.trim().toLowerCase(Locale.ROOT);int count=0;
        for(FileTransfers.Entry e:transfers.entries()){if(!e.name.toLowerCase(Locale.ROOT).contains(q))continue;count++;String date=e.date>0?new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(new Date(e.date)):"";item(c,"↓",e.name,android.text.format.Formatter.formatFileSize(this,e.bytes)+(date.isEmpty()?"":" • "+date),"›",()->downloadEntry(e));}
        if(count==0)add(c,label(q.isEmpty()?"Скачанные файлы появятся здесь":"Файлы не найдены",14,MUTED),-1,48);
    }
    private void downloadEntry(FileTransfers.Entry e){
        LinearLayout c=panel(e.name,"Управление файлом",true);
        item(c,"↗","Открыть","Выбрать приложение","›",()->transfers.open(e));
        item(c,"↗","Поделиться","Отправить файл","›",()->transfers.share(e));
        item(c,"↓","Сохранить копию","Выбрать папку и имя","›",()->transfers.export(e));
        if(e.name.toLowerCase(Locale.ROOT).matches(".*\\.conf(\\.txt)?"))item(c,"◈","Импортировать в VPN","WireGuard","›",()->{try{vpn.importMany(Collections.singletonList(transfers.uri(e)),msg->runOnUiThread(()->{if(!isDestroyed()){vpnPanel();toast(msg);}}));}catch(Exception ex){toast("Не удалось открыть профиль");}});
        item(c,"×","Удалить файл","С устройства","›",()->new AlertDialog.Builder(this).setTitle("Удалить файл?").setMessage(e.name).setNegativeButton("Отмена",null).setPositiveButton("Удалить",(dialog,w)->{if(transfers.delete(e))downloadPanel();else toast("Не удалось удалить файл");}).show());
    }
    private void devSuitePanel(){
        boolean page=!tabs.isEmpty()&&!current().home&&Router.parse(current().url).kind==Router.Kind.WEB;
        LinearLayout c=panel("DevSuite","AetherBolt • Инструменты разработчика",true);
        section(c,"ТЕКУЩАЯ СТРАНИЦА");
        item(c,"JS","Eruda — инспектор",page?"DOM • CSS • сеть • консоль":"Сначала откройте сайт","›",()->{if(!page){toast("Откройте HTTP(S)-страницу");return;}dismissPanel();devSuite.send("eruda");});
        item(c,">_","xterm — JavaScript-консоль",page?"Выполнение JS в активной вкладке":"Сначала откройте сайт","›",()->{if(!page){toast("Откройте HTTP(S)-страницу");return;}dismissPanel();devSuite.send("xterm");});
        section(c,"ЛОКАЛЬНЫЕ ИНСТРУМЕНТЫ");
        item(c,"▤","CyberChef","Кодирование • преобразование данных","›",()->devSuite.openTool("cyberchef/CyberChef_v11.6.0.html"));
        item(c,"▣","Linux v86","Виртуальная машина • ISO • терминал","›",()->devSuite.openTool("v86/index.html"));
        section(c,"СЕТЬ И ФАЙЛЫ");
        item(c,"◈","Прокси SOCKS5 / HTTPS","Настройки соединения GeckoView","›",()->proxyPanel());
        item(c,"↓","Загрузки","Открыть, поделиться, сохранить копию","›",()->downloadPanel());
    }
    private void proxyPanel(){
        LinearLayout c=panel("Прокси","DevSuite • Для всех вкладок GeckoView",true);
        item(c,"◎","Прямое соединение","Отключить прокси","›",()->{devSuite.sendProxy("direct","",0);devSuitePanel();});
        item(c,"◈","SOCKS5","Удалённое разрешение DNS","›",()->proxyForm("socks"));
        item(c,"◈","HTTPS","TLS-соединение с прокси","›",()->proxyForm("https"));
        add(c,label("Прокси не заменяет VPN. Авторизация пока не поддерживается; проверяйте внешний IP.",12,MUTED),-1,-2);
    }
    private void proxyForm(String type){
        LinearLayout c=panel(type.equals("socks")?"SOCKS5":"HTTPS","DevSuite • Настройка сервера",true);
        EditText host=field("Адрес сервера"),port=field("Порт");port.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);port.setText(type.equals("socks")?"1080":"443");add(c,host,-1,52);add(c,port,-1,52);
        item(c,"✓","Подключить","Сохранить на текущий сеанс","›",()->{String h=host.getText().toString().trim();try{int p=Integer.parseInt(port.getText().toString());if(h.isEmpty()||!h.matches("[a-zA-Z0-9.:-]+")||p<1||p>65535)throw new IllegalArgumentException();devSuite.sendProxy(type,h,p);devSuitePanel();}catch(Exception e){host.setError("Проверьте адрес и порт");}});
    }

    private android.graphics.drawable.Drawable icon(String key,int color,int size){BoltIcon d=new BoltIcon(key,color);d.setBounds(0,0,dp(size),dp(size));return d;}
    private GradientDrawable glass(int radius,int stroke){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff242a49,0xff171d34,0xff10162b});d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private android.graphics.drawable.Drawable glassRipple(int radius,int stroke){return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x447f8cff),glass(radius,stroke),null);}
    private void captureShown(){
        if(touchDown)return;
        Tab t=shownTab;if(t==null||t.home||t.extensionOwner!=null||!historyEnabled||!tabs.contains(t)||!t.web.isAttachedToWindow()||!capturing.add(t))return;
        String url=t.url;
        try{t.web.capturePixels().then(bitmap->{
            if(bitmap==null){runOnUiThread(()->capturing.remove(t));return null;}
            new Thread(()->{
                android.graphics.Bitmap thumb=null;
                try{int w=bitmap.getWidth(),h=bitmap.getHeight();if(w>0&&h>0){float scale=Math.min(1f,Math.min(320f/w,200f/h));thumb=android.graphics.Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)),true);}}
                catch(RuntimeException ignored){}
                final android.graphics.Bitmap ready=thumb;
                runOnUiThread(()->{capturing.remove(t);if(!isDestroyed()&&ready!=null&&tabs.contains(t)&&url.equals(t.url)&&historyEnabled)t.preview=ready;});
            },"aether-thumbnail").start();return null;
        },err->{runOnUiThread(()->capturing.remove(t));return null;});}catch(RuntimeException e){capturing.remove(t);}
    }

    private View previewFor(Tab t){FrameLayout frame=new FrameLayout(this);frame.setBackground(glass(13,0xff3d4772));frame.setClipToOutline(true);if(!t.home&&t.preview!=null){ImageView image=new ImageView(this);image.setImageBitmap(t.preview);image.setScaleType(ImageView.ScaleType.CENTER_CROP);frame.addView(image,new FrameLayout.LayoutParams(-1,-1));}else{if(t.home)frame.setBackground(new SpaceArt());LinearLayout center=column();center.setGravity(Gravity.CENTER);ImageView img=new ImageView(this);if(t.home)img.setImageResource(R.drawable.bolt);else if(t.favicon!=null)img.setImageBitmap(t.favicon);else img.setImageDrawable(icon("◎",CYAN,38));add(center,img,46,46);TextView name=label(t.home?"AetherBolt":t.title,12,TEXT);name.setGravity(Gravity.CENTER);name.setMaxLines(2);center.addView(name);frame.addView(center,new FrameLayout.LayoutParams(-1,-1));}return frame;}


    private void loadScripts(){try{JSONArray a=new JSONArray(prefs.getString("userScripts","[]"));for(int i=0;i<Math.min(a.length(),40);i++){JSONObject j=a.getJSONObject(i);String site=ScriptRules.scope(j.getString("site")),code=j.getString("code");if(!site.isEmpty()&&ScriptRules.validate(code).isEmpty())scripts.add(new UserScript(j.getString("id"),j.getString("name"),site,code,j.optBoolean("enabled",false)));}}catch(Exception ignored){}}
    private void saveScripts(){JSONArray a=new JSONArray();try{for(UserScript u:scripts){JSONObject j=new JSONObject();j.put("id",u.id);j.put("name",u.name);j.put("site",u.site);j.put("code",u.code);j.put("enabled",u.enabled);a.put(j);}}catch(JSONException ignored){}prefs.edit().putString("userScripts",a.toString()).apply();}
    private void runScripts(Tab t,String url){/* execution bridge not yet ported */}

    private void scriptPanel(){LinearLayout c=panel("Пользовательские скрипты","AetherBolt Script Engine • JavaScript",false);section(c,"ИМПОРТ");item(c,"+","Открыть JS или ZIP","Один самостоятельный JS в ZIP, до 256 КБ","›",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);try{startActivityForResult(i,SCRIPT_PICK);}catch(Exception e){notice("Импорт","Не удалось открыть системный выбор файлов.");}});item(c,"↗","Загрузить по HTTPS","Без cookies и автоматических обновлений","›",()->scriptUrl());item(c,"JS","Создать / вставить код","Встроенный редактор","›",()->editScript(null,"",""));section(c,"УСТАНОВЛЕННЫЕ • "+scripts.size());if(scripts.isEmpty())item(c,"▤","Пока нет скриптов","Импортируй доверенный JavaScript","",()->{});for(UserScript u:new ArrayList<>(scripts))item(c,"JS",u.name,u.site+" • "+(u.enabled?"Включён":"Выключен"),"›",()->scriptDetails(u));section(c,"БЕЗОПАСНОСТЬ");item(c,"◈","Разрешения","Скрипты имеют доступ к содержимому выбранного сайта","›",()->notice("Важно","Запускай только код из доверенных источников. Скрипт может читать и изменять страницу, включая введённые данные. Нет GM API. Скрипты выключены по умолчанию, ограничены одним точным origin и запускаются вручную. Отключение не останавливает уже запущенный код: перезагрузи страницу."));}
    private void scriptDetails(UserScript u){LinearLayout c=panel(u.name,u.site,false);item(c,"✓",u.enabled?"Отключить":"Включить","Включённый скрипт можно запустить кнопкой на сайте после обновления",u.enabled?"●":"○",()->{if(u.enabled){u.enabled=false;saveScripts();scriptDetails(u);}else{LinearLayout a=panel("Разрешить выполнение?",u.site,true);item(a,"◈","Разрешить скрипт","Код сможет читать и менять страницу","›",()->{u.enabled=true;saveScripts();scriptDetails(u);});item(a,"×","Отмена","","",()->scriptDetails(u));}});item(c,"JS","Редактировать код и сайт","","›",()->editScript(u,u.name,u.code));item(c,"×","Удалить скрипт","","›",()->{LinearLayout a=panel("Удалить скрипт?",u.name,true);item(a,"×","Удалить","","",()->{scripts.remove(u);saveScripts();scriptPanel();});item(a,"‹","Отмена","","",()->scriptDetails(u));});}
    private void editScript(UserScript old,String suggested,String initial){LinearLayout c=panel("Редактор скрипта","Скрипт сохраняется выключенным",false);EditText name=field("Название"),site=field("https://example.com"),code=field("JavaScript");name.setText(suggested);if(old!=null)site.setText(old.site);else if(suggested.toLowerCase(Locale.ROOT).contains("mixamo"))site.setText("https://www.mixamo.com");else if(!current().home&&current().url!=null)site.setText(ScriptRules.origin(current().url));code.setSingleLine(false);code.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);code.setGravity(Gravity.TOP|Gravity.START);code.setTypeface(Typeface.MONOSPACE);code.setTextSize(13);code.setText(initial);add(c,name,-1,54);add(c,site,-1,54);add(c,code,-1,260);TextView info=label("Точный адрес сайта (origin). Включи скрипт, обнови сайт и нажми кнопку запуска. Поддерживаются обычные @match/include/exclude, без GM API.",12,MUTED);add(c,info,-1,-2);item(c,"✓","Сохранить выключенным","","›",()->{String n=name.getText().toString().trim(),o=ScriptRules.scope(site.getText().toString()),source=code.getText().toString(),err=ScriptRules.validate(source);if(n.isEmpty()){name.setError("Укажи название");return;}if(o.isEmpty()){site.setError("Нужен точный HTTP(S) origin");return;}if(!err.isEmpty()){code.setError(err);toast(err);return;}if(old==null&&scripts.size()>=40){toast("Максимум 40 скриптов");return;}if(old==null)scripts.add(new UserScript(UUID.randomUUID().toString(),n,o,source,false));else{old.name=n;old.site=o;old.code=source;old.enabled=false;}saveScripts();scriptPanel();});}
    private String readScript(java.io.InputStream in)throws java.io.IOException{try(java.io.InputStream input=in;java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){if(input==null)throw new java.io.IOException("Нет данных");byte[] buf=new byte[4096];int n;while((n=input.read(buf))!=-1){if(out.size()+n>ScriptRules.LIMIT)throw new java.io.IOException("Лимит 256 КБ");out.write(buf,0,n);}String result=new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);return result.startsWith("\ufeff")?result.substring(1):result;}}
    private void reviewScript(String code,String fallback){if(isFinishing()||isDestroyed())return;String name=ScriptRules.metadata(code,"name");editScript(null,name.isEmpty()?fallback:name,code);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(transfers!=null&&transfers.onActivityResult(request,result,data))return;if(request==706&&result==RESULT_OK){startVpn();return;}if(request==707&&result==RESULT_OK&&data!=null){ArrayList<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){android.content.ClipData clips=data.getClipData();for(int n=0;n<Math.min(clips.getItemCount(),101);n++){Uri u=clips.getItemAt(n).getUri();if(u!=null&&!uris.contains(u))uris.add(u);}}else if(data.getData()!=null)uris.add(data.getData());vpn.importMany(uris,msg->runOnUiThread(()->{if(!isDestroyed()){vpnPanel();toast(msg);}}));return;}if(request==705&&result==RESULT_OK&&data!=null&&data.getData()!=null){applyUpdate(data.getData());return;}if((request==703||request==704)&&result==RESULT_OK&&data!=null&&data.getData()!=null){importLocal(data.getData(),request);return;}if(request!=SCRIPT_PICK||result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();new Thread(()->{try{String[] loaded=readLocalScript(uri);runOnUiThread(()->reviewScript(loaded[1],loaded[0]));}catch(Exception e){runOnUiThread(()->{if(!isDestroyed())notice("Ошибка импорта",String.valueOf(e.getMessage()));});}}).start();}
    private void scriptUrl(){LinearLayout c=panel("Загрузить скрипт","Только прямая HTTPS-ссылка",false);EditText url=field("https://example.com/script.js");add(c,url,-1,54);item(c,"↓","Загрузить для проверки","Не запускается автоматически","›",()->{String raw=url.getText().toString().trim();if(!raw.startsWith("https://")||ScriptRules.origin(raw).isEmpty()){url.setError("Нужна HTTPS-ссылка");return;}notice("Импорт","Получение скрипта…");new Thread(()->{try{java.net.URL u=new java.net.URL(raw);javax.net.ssl.HttpsURLConnection connection=(javax.net.ssl.HttpsURLConnection)u.openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(10000);connection.setReadTimeout(10000);connection.setRequestProperty("Accept","application/javascript, text/javascript, text/plain, */*");try{if(connection.getResponseCode()!=200)throw new java.io.IOException("HTTP "+connection.getResponseCode()+"; перенаправления не поддерживаются");String code=readScript(connection.getInputStream());runOnUiThread(()->reviewScript(code,"Загруженный скрипт"));}finally{connection.disconnect();}}catch(Exception e){runOnUiThread(()->{if(!isDestroyed())notice("Ошибка импорта",String.valueOf(e.getMessage()));});}}).start();});}

    private void configure(Tab t){applyOptions(t);t.session.setPromptDelegate(transfers.promptDelegate());t.session.setNavigationDelegate(new GeckoSession.NavigationDelegate(){
        @Override public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession session,GeckoSession.NavigationDelegate.LoadRequest req){String uri=req.uri;if("about:blank".equals(uri)||(t.extensionOwner!=null&&addonUrl(t.extensionOwner,uri)&&"moz-extension".equals(Uri.parse(uri).getScheme())))return GeckoResult.fromValue(AllowOrDeny.ALLOW);if(FileTransfers.sameOriginBlob(uri,t.url))return GeckoResult.fromValue(AllowOrDeny.ALLOW);if(Router.parse(uri).kind!=Router.Kind.WEB)return GeckoResult.fromValue(AllowOrDeny.DENY);if(uri.startsWith("https://")&&Uri.parse(uri).getPath()!=null&&Uri.parse(uri).getPath().endsWith(".xpi")){confirmXpi(uri);return GeckoResult.fromValue(AllowOrDeny.DENY);}return GeckoResult.fromValue(AllowOrDeny.ALLOW);}
        @Override public GeckoResult<GeckoSession> onNewSession(GeckoSession session,String uri){
            if(isFinishing()||isDestroyed()||!tabs.contains(t))return null;
            if(!"about:blank".equals(uri)&&Router.parse(uri).kind!=Router.Kind.WEB)return null;
            if(tabs.size()>=12){toast("Лимит вкладок: закрой одну и повтори переход");return null;}
            Tab next=new Tab();next.opener=t;next.home=false;next.url=uri;next.title="Новая вкладка";
            next.web=new GeckoView(MainActivity.this);next.session=new GeckoSession();
            configure(next);next.web.setSession(next.session);attachScriptBridge(next);if(devSuite!=null)devSuite.attach(next.session);
            tabs.add(next);active=tabs.size()-1;showTab();
            // GeckoView opens this new session itself and loads the requested URI.
            return GeckoResult.fromValue(next.session);
        }
        @Override public GeckoResult<String> onLoadError(GeckoSession session,String uri,WebRequestError error){if(tabs.contains(t)&&t==current())toast("Не удалось открыть страницу. Проверь сеть и адрес.");return null;}
        @Override public void onLocationChange(GeckoSession session,String url,List<GeckoSession.PermissionDelegate.ContentPermission> perms,Boolean gesture){if(url==null)return;t.url=url;if(tabs.contains(t)&&t==current()&&!t.home&&!address.hasFocus())address.setText(url);}
        @Override public void onCanGoBack(GeckoSession session,boolean value){t.canBack=value;}
        @Override public void onCanGoForward(GeckoSession session,boolean value){t.canForward=value;}
    });t.session.setProgressDelegate(new GeckoSession.ProgressDelegate(){
        @Override public void onPageStart(GeckoSession session,String url){if(fullscreenTab==t)leaveFullscreen();t.progress=1;t.url=url;updateProgress(t);}
        @Override public void onProgressChange(GeckoSession session,int value){t.progress=value;updateProgress(t);}
        @Override public void onPageStop(GeckoSession session,boolean success){t.progress=100;updateProgress(t);if(success&&historyEnabled&&Router.parse(t.url).kind==Router.Kind.WEB)remember(history,"history",t.url,200);}
    });t.session.setContentDelegate(new GeckoSession.ContentDelegate(){
        @Override public void onFullScreen(GeckoSession session,boolean fullScreen){fullscreenChanged(t,fullScreen);}
        @Override public void onTitleChange(GeckoSession session,String title){if(title!=null&&!title.isEmpty()&&!title.equals(t.title)){t.title=title;if(tabs.contains(t))scheduleTitleUpdate();}}
        @Override public void onExternalResponse(GeckoSession session,WebResponse response){saveGeckoDownload(response);}
    });}

    private void initScriptBridge(){extensions.ensureBuiltIn("resource://android/assets/scripts/","scripts@aetherbolt.local").then(e->{runOnUiThread(()->{if(isDestroyed())return;scriptBridge=e;for(Tab t:tabs)attachScriptBridge(t);});return null;},err->{runOnUiThread(()->toast("Script Engine: "+err));return null;});}
    private void attachScriptBridge(Tab tab){if(scriptBridge==null)return;tab.session.getWebExtensionController().setMessageDelegate(scriptBridge,new WebExtension.MessageDelegate(){
        @Override public GeckoResult<Object> onMessage(String nativeApp,Object message,WebExtension.MessageSender sender){
            JSONObject reply=new JSONObject();try{JSONArray list=new JSONArray();reply.put("scripts",list);
                if(!sender.isTopLevel()||sender.session!=tab.session||!tabs.contains(tab)||!(message instanceof JSONObject))return GeckoResult.fromValue(reply);
                JSONObject req=(JSONObject)message;String url=sender.url,site=ScriptRules.origin(url==null?"":url);if(site.isEmpty())return GeckoResult.fromValue(reply);
                String action=req.optString("action");for(UserScript u:scripts){if(!u.enabled||!u.site.equals(site)||!ScriptRules.permits(u.code,url))continue;
                    if("list".equals(action)){JSONObject item=new JSONObject();item.put("id",u.id);item.put("name",u.name);list.put(item);}
                    else if("run".equals(action)&&u.id.equals(req.optString("id"))){reply.put("code",u.code);reply.put("origin",site);break;}
                }
            }catch(JSONException ignored){}return GeckoResult.fromValue(reply);
        }
    },"aetherScripts");}
    private String[] readLocalScript(Uri uri)throws java.io.IOException{
        String name="Импортированный скрипт.js";try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst()&&c.getString(0)!=null)name=c.getString(0);}
        try(java.io.BufferedInputStream raw=new java.io.BufferedInputStream(getContentResolver().openInputStream(uri))){
            raw.mark(4);int a=raw.read(),b=raw.read();raw.reset();
            if(a!='P'||b!='K')return new String[]{name,readScript(raw)};
            String found=null,code=null;int entries=0;long expanded=0;
            try(java.util.zip.ZipInputStream zip=new java.util.zip.ZipInputStream(raw)){java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null){
                if(++entries>200)throw new java.io.IOException("Слишком много файлов в ZIP");if(entry.isDirectory())continue;
                String path=entry.getName();String lower=path.toLowerCase(Locale.ROOT);if(lower.endsWith("/manifest.json")||lower.equals("manifest.json"))throw new java.io.IOException("Это расширение, а не отдельный JS. Нужна совместимая XPI-сборка.");
                boolean jsFile=lower.endsWith(".js")&&!lower.contains("__macosx/");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=zip.read(buf))!=-1){expanded+=n;if(expanded>4L*1024*1024)throw new java.io.IOException("Лимит распакованного ZIP: 4 МБ");if(jsFile){if(out.size()+n>ScriptRules.LIMIT)throw new java.io.IOException("Лимит JS: 256 КБ");out.write(buf,0,n);}}
                if(jsFile){if(found!=null)throw new java.io.IOException("В ZIP несколько JS. Выбери самостоятельный скрипт.");found=path.substring(path.lastIndexOf('/')+1);code=new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);}
            }}
            if(found==null)throw new java.io.IOException("В ZIP нет JS");return new String[]{found,code.startsWith("\ufeff")?code.substring(1):code};
        }
    }

    
    private final Map<String,WebExtension.Action> addonActions=new HashMap<>();
    private final Map<String,WebExtension> registeredAddons=new HashMap<>();
    private Dialog addonPopup;
    private final WebExtension.ActionDelegate addonActionDelegate=new WebExtension.ActionDelegate(){
        @Override public void onBrowserAction(WebExtension e,GeckoSession session,WebExtension.Action a){if(session==null)addonActions.put(e.id,a);}
        @Override public GeckoResult<GeckoSession> onTogglePopup(WebExtension e,WebExtension.Action a){if(addonPopup!=null&&addonPopup.isShowing()){addonPopup.dismiss();return null;}return openAddonPopup(e);}
        @Override public GeckoResult<GeckoSession> onOpenPopup(WebExtension e,WebExtension.Action a){if(addonPopup!=null)addonPopup.dismiss();return openAddonPopup(e);}
    };
    private boolean addonUrl(WebExtension e,String url){if(url==null||"about:blank".equals(url)||Router.parse(url).kind==Router.Kind.WEB)return true;if(e.metaData==null||e.metaData.baseUrl==null)return false;Uri base=Uri.parse(e.metaData.baseUrl),target=Uri.parse(url);return "moz-extension".equals(target.getScheme())&&base.getHost()!=null&&base.getHost().equals(target.getHost());}
    private void registerAddon(WebExtension e){registeredAddons.put(e.id,e);e.setActionDelegate(addonActionDelegate);e.setTabDelegate(new WebExtension.TabDelegate(){
        @Override public GeckoResult<GeckoSession> onNewTab(WebExtension ext,WebExtension.CreateTabDetails details){if(isDestroyed()||isFinishing()||tabs.size()>=12||!addonUrl(ext,details.url))return null;Tab t=new Tab();t.opener=current();t.home=false;t.url=details.url==null?"about:blank":details.url;t.extensionOwner=ext;t.web=new GeckoView(MainActivity.this);t.session=new GeckoSession();configure(t);t.web.setSession(t.session);attachScriptBridge(t);if(devSuite!=null)devSuite.attach(t.session);tabs.add(t);if(!Boolean.FALSE.equals(details.active)){if(addonPopup!=null)addonPopup.dismiss();active=tabs.size()-1;showTab();}else refreshTabs();return GeckoResult.fromValue(t.session);}
    });}
    private GeckoResult<GeckoSession> openAddonPopup(WebExtension e){if(isFinishing()||isDestroyed())return null;dismissPanel();GeckoSession popup=new GeckoSession();GeckoView view=new GeckoView(this);popup.setPromptDelegate(transfers.promptDelegate());popup.open(runtime);view.setSession(popup);Dialog dialog=new Dialog(this);LinearLayout box=column();box.setBackgroundColor(0xff111827);TextView title=label(extName(e),16,TEXT);add(box,title,-1,44);add(box,button("×","Закрыть",()->dialog.dismiss()),-1,44);box.addView(view,new LinearLayout.LayoutParams(-1,0,1));dialog.setContentView(box);addonPopup=dialog;dialog.setOnDismissListener(v->{view.releaseSession();popup.close();if(addonPopup==dialog)addonPopup=null;});dialog.show();if(dialog.getWindow()!=null){dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels,dp(480)),Math.min((int)(getResources().getDisplayMetrics().heightPixels*.82),dp(760)));dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}return GeckoResult.fromValue(popup);}
    private void launchAddon(WebExtension e){WebExtension.Action action=addonActions.get(e.id);if(action==null){notice("Расширение","У расширения пока нет доступной панели. Проверь, что оно включено, и повтори после загрузки страницы.");return;}dismissPanel();action.click();}
    private void openOrInstallProton(){extensions.list().then(list->{runOnUiThread(()->{if(isDestroyed())return;for(WebExtension e:list){if(extName(e).toLowerCase(Locale.ROOT).contains("proton vpn")){if(!registeredAddons.containsKey(e.id))registerAddon(e);if(e.metaData!=null&&e.metaData.enabled)launchAddon(e);else extensionDetail(e);return;}}installProtonAddon();});return null;},err->{runOnUiThread(()->notice("Proton VPN",String.valueOf(err)));return null;});}
    private void installProtonAddon(){confirmXpi("https://addons.mozilla.org/firefox/downloads/latest/proton-vpn-firefox-extension/latest.xpi");}

    private void configureExtensions(){extensions.setPromptDelegate(new WebExtensionController.PromptDelegate(){
        @Override public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(WebExtension e,String[] p,String[] o,String[] d){GeckoResult<WebExtension.PermissionPromptResponse> r=new GeckoResult<>();runOnUiThread(()->{if(isFinishing()||isDestroyed()){r.complete(new WebExtension.PermissionPromptResponse(false,false,false));return;}new AlertDialog.Builder(MainActivity.this).setTitle("Установить "+extName(e)+"?").setMessage("Разрешения: "+Arrays.toString(p)+"\nСайты: "+Arrays.toString(o)+"\nДанные: "+Arrays.toString(d)).setPositiveButton("Установить",(dialog,which)->r.complete(new WebExtension.PermissionPromptResponse(true,false,false))).setNegativeButton("Отмена",(dialog,which)->r.complete(new WebExtension.PermissionPromptResponse(false,false,false))).setOnCancelListener(dialog->r.complete(new WebExtension.PermissionPromptResponse(false,false,false))).show();});return r;}
        @Override public GeckoResult<AllowOrDeny> onOptionalPrompt(WebExtension e,String[] p,String[] o,String[] d){GeckoResult<AllowOrDeny> result=new GeckoResult<>();if(isFinishing()||isDestroyed()){result.complete(AllowOrDeny.DENY);return result;}new AlertDialog.Builder(MainActivity.this).setTitle(extName(e)+" • Разрешения").setMessage("Разрешения: "+Arrays.toString(p)+"\nСайты: "+Arrays.toString(o)+"\nДанные: "+Arrays.toString(d)).setPositiveButton("Разрешить",(dialog,which)->result.complete(AllowOrDeny.ALLOW)).setNegativeButton("Отмена",(dialog,which)->result.complete(AllowOrDeny.DENY)).setOnCancelListener(dialog->result.complete(AllowOrDeny.DENY)).show();return result;}
    });extensions.list().then(list->{runOnUiThread(()->{if(!isDestroyed())for(WebExtension e:list)registerAddon(e);});return null;},err->null);}
    private String extName(WebExtension e){return e.metaData!=null&&e.metaData.name!=null?e.metaData.name:e.id;}
    private void installFinder(){extensions.ensureBuiltIn("resource://android/assets/finder/","finder@aetherbolt.local").then(e->{runOnUiThread(()->extensionList());return null;},err->{runOnUiThread(()->notice("Ошибка модуля",String.valueOf(err)));return null;});}
    private void extensionUrl(){LinearLayout c=panel("Установить XPI","Прямая HTTPS-ссылка",false);EditText u=field("https://.../addon.xpi");add(c,u,-1,54);item(c,"↓","Продолжить","","›",()->{String url=u.getText().toString().trim();if(!url.startsWith("https://")||ScriptRules.origin(url).isEmpty()){u.setError("Нужна HTTPS-ссылка");return;}confirmXpi(url);});}
    private void confirmXpi(String url){new AlertDialog.Builder(this).setTitle("Загрузить расширение?").setMessage(url).setPositiveButton("Продолжить",(d,w)->installXpi(url)).setNegativeButton("Отмена",null).show();}
    private void installXpi(String uri){String method=uri.startsWith("file:")?WebExtensionController.INSTALLATION_METHOD_FROM_FILE:WebExtensionController.INSTALLATION_METHOD_MANAGER;extensions.install(uri,method).then(e->{runOnUiThread(()->{if(isDestroyed())return;registerAddon(e);toast("Расширение установлено");dismissPanel();quickAddons();});return null;},err->{runOnUiThread(()->showAddonInstallError(err));return null;});}
    private void showAddonInstallError(Throwable error){if(isDestroyed())return;Throwable cause=error;while(cause!=null&&!(cause instanceof WebExtension.InstallException)&&cause.getCause()!=cause)cause=cause.getCause();String detail=String.valueOf(error);if(cause instanceof WebExtension.InstallException){int code=((WebExtension.InstallException)cause).code;String hint;switch(code){case WebExtension.InstallException.ErrorCodes.ERROR_INCOMPATIBLE:hint="Расширение несовместимо с Android / GeckoView.";break;case WebExtension.InstallException.ErrorCodes.ERROR_SIGNEDSTATE_REQUIRED:hint="Подпись Mozilla отсутствует или не принята.";break;case WebExtension.InstallException.ErrorCodes.ERROR_CORRUPT_FILE:hint="Движок не принял содержимое XPI.";break;case WebExtension.InstallException.ErrorCodes.ERROR_NETWORK_FAILURE:hint="Ошибка скачивания расширения.";break;case WebExtension.InstallException.ErrorCodes.ERROR_FILE_ACCESS:hint="Ошибка доступа к файлу.";break;case WebExtension.InstallException.ErrorCodes.ERROR_USER_CANCELED:hint="Установка отменена.";break;default:hint="GeckoView отклонил установку.";}detail=hint+"\\nКод GeckoView: "+code+"\\n"+detail;}final String report=detail;new AlertDialog.Builder(this).setTitle("Ошибка установки XPI").setMessage(report).setPositiveButton("OK",null).setNeutralButton("Копировать",(d,w)->{android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(android.content.ClipData.newPlainText("XPI error",report));}).show();}
    private void extensionList(){LinearLayout c=panel("Расширения","Менеджер GeckoView",false);extensions.list().then(list->{runOnUiThread(()->{if(isDestroyed()||!c.isAttachedToWindow())return;if(list.isEmpty())section(c,"НЕТ РАСШИРЕНИЙ");for(WebExtension e:list){if(!visibleAddon(e))continue;if(!registeredAddons.containsKey(e.id))registerAddon(e);boolean enabled=e.metaData!=null&&e.metaData.enabled;item(c,"✧",extName(e),enabled?"Включено":"Выключено","›",()->extensionDetail(e));}});return null;},err->{runOnUiThread(()->toast("Ошибка списка: "+err));return null;});}
    private void extensionDetail(WebExtension e){boolean enabled=e.metaData!=null&&e.metaData.enabled;LinearLayout c=panel(extName(e),e.id,false);if(enabled)item(c,"↗","Открыть расширение","Панель расширения","›",()->launchAddon(e));item(c,"✓",enabled?"Отключить":"Включить","Действие на уровне движка",enabled?"●":"○",()->{GeckoResult<WebExtension> result=enabled?extensions.disable(e,WebExtensionController.EnableSource.USER):extensions.enable(e,WebExtensionController.EnableSource.USER);result.then(updated->{runOnUiThread(()->{registerAddon(updated);extensionDetail(updated);});return null;},err->{runOnUiThread(()->notice("Ошибка",String.valueOf(err)));return null;});});item(c,"×","Удалить","","›",()->new AlertDialog.Builder(this).setTitle("Удалить "+extName(e)+"?").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->extensions.uninstall(e).then(v->{runOnUiThread(()->extensionList());return null;},err->{runOnUiThread(()->notice("Ошибка",String.valueOf(err)));return null;})).show());}
    private boolean updateBusy=false; private java.io.File preparedUpdate;
    private void updatePanel(){LinearLayout c=panel("Обновление браузера","AetherBolt Delta • локальные патчи",false);item(c,"+","Открыть патч .aeb","Только для установленной версии и той же подписи","›",()->pickLocal(705));if(preparedUpdate!=null&&preparedUpdate.isFile())item(c,"✓","Установить обновление","Подтверждение через Android","›",()->installPrepared());}
    private void applyUpdate(Uri uri){if(updateBusy){toast("Патч уже обрабатывается");return;}updateBusy=true;notice("Обновление","Восстановление APK и проверка подписи…");new Thread(()->{try(java.io.InputStream in=getContentResolver().openInputStream(uri)){java.io.File f=DeltaUpdate.apply(this,in);runOnUiThread(()->{updateBusy=false;if(isDestroyed()){f.delete();return;}if(preparedUpdate!=null)preparedUpdate.delete();preparedUpdate=f;dismissPanel();updatePanel();installPrepared();});}catch(Exception ex){runOnUiThread(()->{updateBusy=false;if(!isDestroyed())notice("Ошибка обновления",String.valueOf(ex.getMessage()));});}}).start();}
    private void installPrepared(){if(preparedUpdate==null||!preparedUpdate.isFile())return;try{if(!getPackageManager().canRequestPackageInstalls()){startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));return;}Uri u=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".updates",preparedUpdate);startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(u,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}catch(Exception ex){notice("Установка",String.valueOf(ex.getMessage()));}}
    private void pickLocal(int request){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,request);}
    private void localArchives(){LinearLayout c=panel("Личные файлы","Только на устройстве",false);item(c,"+","Сохранить ZIP / JS","Хранение исходников; запуск ещё не подключён","›",()->pickLocal(704));java.io.File dir=new java.io.File(getFilesDir(),"private-addons");java.io.File[] entries=dir.listFiles();if(entries!=null)for(java.io.File f:entries)item(c,"▤",f.getName(),f.length()+" байт • не исполняется","×",()->new AlertDialog.Builder(this).setTitle("Удалить локальный файл?").setPositiveButton("Удалить",(d,w)->{f.delete();localArchives();}).setNegativeButton("Отмена",null).show());}
    private void importLocal(Uri uri,int request){new Thread(()->{java.io.File out=null;try{String name="addon";try(android.database.Cursor cur=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(cur!=null&&cur.moveToFirst())name=cur.getString(0);}name=name==null?"addon":name.replaceAll("[^\\p{L}\\p{N}._ -]","_");if(name.length()>100)name=name.substring(name.length()-100);if(request==703&&!name.toLowerCase(Locale.ROOT).endsWith(".xpi"))throw new java.io.IOException("Выбери .xpi");java.io.File dir=new java.io.File(getFilesDir(),request==703?"xpi-import":"private-addons");dir.mkdirs();out=new java.io.File(dir,UUID.randomUUID().toString().substring(0,8)+"-"+name);try(java.io.InputStream in=getContentResolver().openInputStream(uri);java.io.OutputStream dest=new java.io.FileOutputStream(out)){byte[] buf=new byte[8192];long sum=0;int n;while((n=in.read(buf))!=-1){sum+=n;if(sum>64L*1024*1024)throw new java.io.IOException("Лимит 64 МБ");dest.write(buf,0,n);}}final java.io.File f=out;runOnUiThread(()->{if(isDestroyed())return;if(request==703)installXpi(Uri.fromFile(f).toString());else localArchives();});}catch(Exception e){if(out!=null)out.delete();runOnUiThread(()->notice("Ошибка импорта",String.valueOf(e)));}}).start();}
    private void downloadActions(java.io.File f){String lower=f.getName().toLowerCase(Locale.ROOT);if(f.isFile()&&(lower.endsWith(".conf")||lower.endsWith(".conf.txt"))){new AlertDialog.Builder(this).setTitle("VPN-профиль").setMessage("Импортировать "+f.getName()+" в VPN Manager?").setPositiveButton("Импортировать",(dialog,which)->{try{Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".updates",f);vpn.importMany(Collections.singletonList(uri),msg->runOnUiThread(()->{if(!isDestroyed()){vpnPanel();toast(msg);}}));}catch(Exception ex){notice("VPN","Не удалось открыть сохранённый файл");}}).setNegativeButton("Отмена",null).show();}else notice("Файл",f.getAbsolutePath());}
    private void saveGeckoDownload(WebResponse response){transfers.receive(response);}
    private void closeResponse(WebResponse r){try{if(r.body!=null)r.body.close();}catch(Exception ignored){}}


    private boolean isAdHost(String h){if(h==null)return false;h=h.toLowerCase(Locale.ROOT);for(String domain:new String[]{"doubleclick.net","googlesyndication.com","googleadservices.com","adnxs.com","adsrvr.org"})if(h.equals(domain)||h.endsWith("."+domain))return true;return false;}
    private void navigate(String raw){Router.Target target=Router.resolve(raw,SearchLanguage.template(engineTemplate,prefs.getString("searchLanguage","all")));if(target.kind==Router.Kind.ONION){notice("Сеть Tor","Распознан .onion-адрес. Tor пока не реализован, поэтому адрес не загружен.");return;}if(target.kind==Router.Kind.INVALID){toast("Введите корректный HTTP(S)-адрес или запрос");return;}((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);Tab t=current();t.home=false;showTab();address.setText(target.url);t.session.load(new GeckoSession.Loader().uri(target.url));}
    private void back(){if(fullscreenUi){leaveFullscreen();return;}if(overlay!=null){panelBack();return;}Tab t=current();if(!t.home&&t.canBack){t.session.goBack();return;}if(t.opener!=null&&tabs.contains(t.opener)){Tab parent=t.opener;closeTab(active);active=tabs.indexOf(parent);showTab();return;}if(!t.home){toast("Нет предыдущей страницы");return;}super.onBackPressed();}
    @Override public void onBackPressed(){back();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void setEngine(String name,String template){engineName=name;engineTemplate=template;prefs.edit().putString("engineName",name).putString("engineTemplate",template).apply();if(current().home)showTab();toast("Поисковик: "+name);}
    private void engineDialog(){LinearLayout c=panel("Поисковая система","Выбери основной поисковик",false);String[] symbols={"G","D","b","B","Q","Я","Y","S","E","M","◎","S"};for(int i=0;i<Router.ENGINES.length;i++){final int n=i;String name=Router.ENGINES[i][0];item(c,symbols[i],name,"",engineName.equals(name)?"●":"○",()->{dismissPanel();setEngine(Router.ENGINES[n][0],Router.ENGINES[n][1]);});}item(c,"◎","Язык результатов",searchLanguageName(),"›",()->searchLanguages());item(c,"+","Добавить свою","SearXNG и другие","›",()->customEngine());}
    private void customEngine(){LinearLayout c=panel("Свой поисковик","Укажи название и адрес поиска",false);EditText name=field("Название"),url=field("https://example.org/search?q={query}");add(c,name,-1,54);add(c,url,-1,54);TextView hint=label("Используй {query} вместо поискового запроса.",13,MUTED);add(c,hint,-1,-2);item(c,"✓","Сохранить","","›",()->{String n=name.getText().toString().trim(),u=url.getText().toString().trim();if(n.isEmpty()||!Router.validTemplate(u)){url.setError("Нужен HTTP(S)-шаблон с {query}");return;}dismissPanel();setEngine(n,u);});}
    private void tabDialog(){
        captureShown();LinearLayout c=panel("Вкладки","Открытые страницы • "+tabs.size(),false);
        LinearLayout actions=row();add(actions,button("+ Новая","Новая вкладка",()->addTab()),-2,48);add(actions,button("↶","Вернуть закрытую вкладку",()->reopenTab()),48,48);add(actions,button("×","Закрыть остальные",()->{new AlertDialog.Builder(this).setTitle("Закрыть остальные вкладки?").setNegativeButton("Отмена",null).setPositiveButton("Закрыть",(d,w)->{Tab keep=current();for(int i=tabs.size()-1;i>=0;i--)if(tabs.get(i)!=keep)closeTab(i);tabDialog();}).show();}),48,48);c.addView(actions);
        EditText search=field("Поиск по вкладкам");add(c,search,-1,48);
        LinearLayout cards=column();c.addView(cards);
        Runnable render=()->renderTabCards(cards,search.getText().toString());
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence x,int a,int n,int f){}public void onTextChanged(CharSequence x,int a,int b,int n){render.run();}public void afterTextChanged(android.text.Editable x){}});
        render.run();
    }
    private void renderTabCards(LinearLayout cards,String query){
        cards.removeAllViews();String q=query.trim().toLowerCase(Locale.ROOT);
        ArrayList<Tab> matches=new ArrayList<>();for(Tab t:tabs)if(q.isEmpty()||(t.title+" "+t.url).toLowerCase(Locale.ROOT).contains(q))matches.add(t);
        int cols=getResources().getConfiguration().screenWidthDp>=840?3:2;
        for(int i=0;i<matches.size();i+=cols){LinearLayout r=row();r.setGravity(Gravity.TOP);for(int j=i;j<i+cols;j++){
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(182),1);lp.setMargins(dp(4),dp(4),dp(4),dp(4));
            if(j>=matches.size()){r.addView(new View(this),lp);continue;}Tab t=matches.get(j);
            LinearLayout card=column();card.setPadding(dp(6),dp(6),dp(6),dp(6));card.setBackground(glassRipple(14,t==current()?CYAN:0xff34405c));r.addView(card,lp);
            LinearLayout h=row();TextView title=label(t.home?"Новая вкладка":t.title,14,TEXT);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);h.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            add(h,button("×","Закрыть "+title.getText(),()->{int n=tabs.indexOf(t);if(n>=0)closeTab(n);tabDialog();}),44,44);card.addView(h);
            View preview=previewFor(t);card.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
            TextView url=label(t.home?"Стартовая страница":t.url,11,MUTED);url.setSingleLine(true);url.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(url);
            View.OnClickListener choose=v->{int n=tabs.indexOf(t);if(n>=0){active=n;showTab();}};card.setOnClickListener(choose);preview.setOnClickListener(choose);title.setOnClickListener(choose);
        }cards.addView(r);}if(matches.isEmpty())add(cards,label("Нет совпадений",15,MUTED),-1,44);
    }
    private void reopenTab(){if(closedPages.isEmpty()){toast("Нет закрытых вкладок");return;}if(tabs.size()>=12){toast("Закройте одну вкладку");return;}String url=closedPages.removeFirst();addTab();navigate(url);}
    private void saveTabs(){
        if(prefs==null)return;
        if(!historyEnabled){for(Tab t:tabs)t.preview=null;closedPages.clear();prefs.edit().remove("workspaceTabs").remove("workspaceActive").apply();return;}
        JSONArray a=new JSONArray();int selected=0;for(Tab t:tabs){if(t.extensionOwner!=null)continue;if(t==current())selected=a.length();if(t.home)a.put("");else if(Router.parse(t.url).kind==Router.Kind.WEB)a.put(t.url);}
        prefs.edit().putString("workspaceTabs",a.toString()).putInt("workspaceActive",selected).apply();
    }
    private void restoreTabs(){
        ArrayList<String> urls=new ArrayList<>();if(historyEnabled)readList("workspaceTabs",urls);int index=prefs.getInt("workspaceActive",0);
        if(urls.isEmpty()){addTab();return;}for(String url:urls.subList(0,Math.min(12,urls.size()))){Tab t=createTab();if(Router.parse(url).kind==Router.Kind.WEB){t.home=false;t.url=url;t.pendingUrl=url;t.title=Uri.parse(url).getHost()==null?url:Uri.parse(url).getHost();}}
        active=Math.max(0,Math.min(index,tabs.size()-1));showTab();
    }
    private void openLocalTool(WebExtension e,String page){
        if(e==null||e.metaData==null||e.metaData.baseUrl==null){toast("Локальные инструменты ещё загружаются");return;}
        if(tabs.size()>=12){toast("Закройте одну вкладку");return;}Tab parent=current();addTab();Tab t=current();t.opener=parent;t.extensionOwner=e;t.home=false;t.url=e.metaData.baseUrl+page;showTab();t.session.load(new GeckoSession.Loader().uri(t.url));
    }
    private void applyProtection(){
        runtime.getSettings().getContentBlocking().setAntiTracking(tracker?ContentBlocking.AntiTracking.DEFAULT:0);
        runtime.getSettings().getContentBlocking().setCookieBehavior(tracker?ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS:ContentBlocking.CookieBehavior.ACCEPT_ALL);
    }
    @Override protected void onPause(){if(!tabs.isEmpty())saveTabs();super.onPause();}
    private void menu(){
        LinearLayout c=panel("AetherBolt","0.7.8 DevSuite • Быстрый доступ",true);
        grid(c,new String[]{"Новая вкладка","Вкладки","Вперёд","Закладка","История","Загрузки","Расширения","DevSuite","VPN","Защита","Настройки","Закладки"},new String[]{"+","▣","›","☆","◷","↓","✧",">_","◈","◈","⚙","☆"},new int[]{CYAN,PURPLE},new Runnable[]{()->addTab(),()->tabDialog(),()->{dismissPanel();if(!current().home&&current().canForward)current().session.goForward();},()->saveBookmark(),()->listDialog("История",history,"history"),()->downloadPanel(),()->quickAddons(),()->devSuitePanel(),()->vpnPanel(),()->privacy(),()->settings(),()->listDialog("Закладки",bookmarks,"bookmarks")},3);
    }

    private String searchLanguageName(){switch(prefs.getString("searchLanguage","all")){case "uk":return "Українська";case "ru":return "Русский";case "en":return "English";default:return "Все языки";}}
    private void searchLanguages(){LinearLayout c=panel("Язык результатов","Фильтр поиска из адресной строки",false);String[] ids={"all","uk","ru","en"},names={"Все языки","Українська","Русский","English"};for(int i=0;i<ids.length;i++){final String id=ids[i];item(c,"◎",names[i],i==0?"Без ограничения":"Предпочтение языка страниц",id.equals(prefs.getString("searchLanguage","all"))?"●":"○",()->{prefs.edit().putString("searchLanguage",id).apply();searchLanguages();});}section(c,"ТЕКУЩИЙ ПОИСКОВИК");item(c,"⌕",engineName,SearchLanguage.support(engineTemplate),"›",()->engineDialog());item(c,"✓","Как это работает","Google / Brave — языковой фильтр; DuckDuckGo — регион; другие пока без фильтра. Прямые ссылки не меняются.","",()->{});}
    private void vpnPanel(){LinearLayout c=panel("AetherBolt VPN Manager","WireGuard • разные провайдеры",false);section(c,"ПОДКЛЮЧЕНИЕ");item(c,"◈",vpn.active()?"Туннель включён":vpn.busy()?"Обработка":"Туннель выключен",vpn.ready()?vpn.selected():"Нужен профиль WireGuard .conf",vpn.active()?"●":"○",()->{if(vpn.busy())return;if(vpn.active())vpn.change(false,this::vpnDone);else if(!vpn.ready())notice("VPN","Получить .conf можно у VPN-провайдера или на своём сервере.");else{Intent permission=android.net.VpnService.prepare(this);if(permission!=null)startActivityForResult(permission,706);else startVpn();}});item(c,"+","Импортировать серверы","Один ZIP или несколько .conf / .conf.txt","›",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,707);});item(c,"×","Удалить выбранный профиль","","›",()->{new AlertDialog.Builder(this).setTitle("Удалить профиль?").setMessage(vpn.selected()).setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->vpn.forget(this::vpnDone)).show();});section(c,"ПРОВАЙДЕРЫ");item(c,"P","Proton VPN • расширение","Открыть или установить • Free: случайная смена сервера","›",()->openOrInstallProton());item(c,"P","Proton VPN • WireGuard","Официальный кабинет • скачать профиль","›",()->openVpnSite("https://account.protonvpn.com/downloads"));item(c,"◎","Каталог Proton","Актуальные страны и серверы на сайте","›",()->openVpnSite("https://protonvpn.com/vpn-servers"));item(c,"+","Другие провайдеры","Импорт любых совместимых WireGuard-профилей","›",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,707);});renderVpnProfiles(c);item(c,"↻","Обновить статус","Туннель UP не доказывает доступность сервера","›",()->vpnPanel());item(c,"⚙","Системные настройки VPN","Системный статус и разрешения","›",()->{try{startActivity(new Intent(android.provider.Settings.ACTION_VPN_SETTINGS));}catch(Exception e){toast("Открой настройки Android");}});section(c,"ВАЖНО");item(c,"◎","Нужен VPN-сервер","Сервер не предоставляется. При отключении VPN обычное соединение возвращается. Защита от утечек ещё не проверена.","",()->{});}

    private void openVpnSite(String url){if(tabs.size()>=12){notice("VPN","Закрой одну вкладку, чтобы открыть сайт провайдера.");return;}Tab previous=current();dismissPanel();addTab();current().opener=previous;navigate(url);}
    private String vpnGroup(String profile){java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)(?:^|[^a-z])([a-z]{2})[-_ ](?:FREE[-_ ]?)?\\d+(?:[^0-9]|$)").matcher(profile);if(!m.find())return "Другие профили";String code=m.group(1).toUpperCase(Locale.ROOT);String country=new Locale("",code).getDisplayCountry(new Locale("ru"));return country.equals(code)?"Другие профили":country+" • "+code;}
    private void renderVpnProfiles(LinearLayout c){List<String> all=vpn.profiles();Set<String> fav=new HashSet<>(prefs.getStringSet("vpnFavorites",Collections.emptySet()));fav.retainAll(all);section(c,"ВЫБОР СТРАНЫ • "+all.size()+" СЕРВЕРОВ");item(c,"☆","Избранное",fav.size()+" сохранено","›",()->{LinearLayout p=panel("Избранные VPN","Выбери сервер",false);for(String s:all)if(fav.contains(s))vpnProfileRow(p,s,true);});Map<String,List<String>> groups=new TreeMap<>();for(String s:all)groups.computeIfAbsent(vpnGroup(s),k->new ArrayList<>()).add(s);for(Map.Entry<String,List<String>> e:groups.entrySet()){String country=e.getKey();List<String> servers=e.getValue();item(c,"◎",country,servers.size()+" серверов","›",()->{LinearLayout p=panel(country,"Выбери сервер для подключения",false);for(String name:servers)vpnProfileRow(p,name,fav.contains(name));});}}
    private void vpnProfileRow(LinearLayout c,String name,boolean favorite){boolean chosen=name.equals(vpn.selected());LinearLayout r=row();r.setBackground(glassRipple(14,0xff343c64));TextView connect=button(name+(chosen?(vpn.active()?" • UP":" • выбран"):""),"Подключить "+name,()->connectProfile(name));connect.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);connect.setTextSize(14);r.addView(connect,new LinearLayout.LayoutParams(0,dp(56),1));add(r,button(favorite?"★":"☆","Избранное",()->{Set<String> f=new HashSet<>(prefs.getStringSet("vpnFavorites",Collections.emptySet()));if(!f.add(name))f.remove(name);prefs.edit().putStringSet("vpnFavorites",f).apply();vpnPanel();}),44,48);add(c,r,-1,-2);}
    private void connectProfile(String name){if(vpn.busy()){toast("VPN обрабатывает запрос");return;}if(vpn.active()&&name.equals(vpn.selected())){toast("Этот туннель уже включён");return;}if(vpn.active()){new AlertDialog.Builder(this).setTitle("Сменить сервер?").setMessage("При смене VPN обычное соединение может временно восстановиться.").setNegativeButton("Отмена",null).setPositiveButton("Сменить",(d,w)->vpn.change(false,err->runOnUiThread(()->{if(isDestroyed())return;if(err!=null)vpnDone(err);else selectAndConnect(name);}))).show();}else selectAndConnect(name);}
    private void selectAndConnect(String name){vpn.select(name,err->runOnUiThread(()->{if(isDestroyed())return;if(err!=null){vpnDone(err);return;}Intent permission=android.net.VpnService.prepare(this);if(permission!=null)startActivityForResult(permission,706);else startVpn();}));}
    private void startVpn(){vpn.change(true,this::vpnDone);}
    private void vpnDone(String err){runOnUiThread(()->{if(isFinishing()||isDestroyed())return;if(err!=null)notice("VPN",err);else vpnPanel();});}

    private void settings(){LinearLayout c=panel("Настройки","Управление AetherBolt Browser",false);section(c,"ПЕРСОНАЛИЗАЦИЯ");item(c,"⌕","Поисковая система",engineName,"›",()->engineDialog());item(c,"◎","Язык результатов",searchLanguageName()+" • "+SearchLanguage.support(engineTemplate),"›",()->searchLanguages());item(c,"✧","Внешний вид",appearance,"›",()->themes());item(c,"▣","Настройки сайтов","JavaScript, изображения, ПК, масштаб, медиа","›",()->webOptions());section(c,"БЕЗОПАСНОСТЬ И МОДУЛИ");item(c,">_","DevSuite","Eruda • xterm • CyberChef • Linux • прокси","›",()->devSuitePanel());item(c,"◈","VPN • WireGuard",vpn.active()?"Туннель включён":"Отключён","›",()->vpnPanel());item(c,"◈","Конфиденциальность","Cookies, история, реклама и трекеры","›",()->privacy());item(c,"✧","Расширения и скрипты","GeckoView • XPI / JS","›",()->addonManage());item(c,"◎","Сеть Tor","Не реализована","›",()->notice("Tor","Поддержка .onion пока не реализована."));section(c,"ФАЙЛЫ И ДАННЫЕ");item(c,"↓","Загрузки","Менеджер файлов","›",()->downloadPanel());item(c,"×","Очистить данные","История и данные сайтов","›",()->clearData());section(c,"О ПРИЛОЖЕНИИ");item(c,"↻","Обновление браузера","Локальный дельта-патч .aeb","›",()->updatePanel());item(c,"⚡","AetherBolt Browser","0.7.8 Alpha • GeckoView 161","›",()->notice("AetherBolt","Beyond the Limits. GeckoView • XPI. Tor и перенос пользовательских скриптов ещё не завершены."));}
    private void clearData(){history.clear();closedPages.clear();for(Tab t:tabs)t.preview=null;prefs.edit().remove("workspaceTabs").remove("workspaceActive").apply();writeList("history",history);notice("История очищена","Данные сайтов Gecko не удалены; отдельный механизм ещё не перенесён.");}

    private void readList(String key,ArrayList<String> out){try{JSONArray a=new JSONArray(prefs.getString(key,"[]"));for(int i=0;i<a.length();i++)out.add(a.getString(i));}catch(JSONException ignored){}}
    private void writeList(String key,ArrayList<String> data){prefs.edit().putString(key,new JSONArray(data).toString()).apply();}
    private void remember(ArrayList<String> data,String key,String url,int cap){data.remove(url);data.add(0,url);while(data.size()>cap)data.remove(data.size()-1);writeList(key,data);}
    private void saveBookmark(){Tab t=current();String u=t.url;if(t.home||u==null||Router.parse(u).kind!=Router.Kind.WEB){toast("Сначала откройте сайт");return;}remember(bookmarks,"bookmarks",u,200);toast("Закладка сохранена");}
    private void listDialog(String title,ArrayList<String> data,String key){LinearLayout c=panel(title,data.size()+" записей",false);if(data.isEmpty()){item(c,"☆","Пока пусто","Здесь появятся сохранённые страницы","",()->{});return;}for(String u:new ArrayList<>(data)){String host=Uri.parse(u).getHost();item(c,key.equals("history")?"◷":"☆",host==null?u:host,u,"›",()->{dismissPanel();navigate(u);});}item(c,"×","Очистить список","","›",()->{LinearLayout confirm=panel("Очистить список?","Действие нельзя отменить",true);item(confirm,"×","Удалить все записи","","",()->{data.clear();writeList(key,data);listDialog(title,data,key);});item(confirm,"‹","Отмена","","",()->listDialog(title,data,key));});}
    @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);tabStrip.setVisibility(!fullscreenUi&&c.screenWidthDp>=600?View.VISIBLE:View.GONE);if(!fullscreenUi)showTab();else shell.requestApplyInsets();}
    @Override protected void onDestroy(){if(fullscreenUi)leaveFullscreen();chromeHandler.removeCallbacksAndMessages(null);if(transfers!=null)transfers.destroy();if(devSuite!=null)devSuite.stop();if(addonPopup!=null)addonPopup.dismiss();for(WebExtension e:registeredAddons.values()){e.setActionDelegate(null);e.setTabDelegate(null);}if(body!=null)body.removeAllViews();for(Tab t:tabs){t.web.releaseSession();t.session.close();}super.onDestroy();}
}
