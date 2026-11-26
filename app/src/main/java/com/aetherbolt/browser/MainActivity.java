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
import android.webkit.*;
import android.widget.*;
import java.io.ByteArrayInputStream;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {
    private static final int BG=0xff0b1020, PANEL=0xff171e35, TEXT=0xffedf2ff, MUTED=0xffa5b1ce, PURPLE=0xffa78bfa, CYAN=0xff53ddfb;
    private LinearLayout root, tabsBar, toolBar; private FrameLayout body; private EditText address;
    private ProgressBar progress; private android.content.SharedPreferences prefs;
    private final ArrayList<Tab> tabs=new ArrayList<>(); private int active=0;
    private static class Tab { WebView web; boolean home=true; String title="Новая вкладка"; int progress=0; }
    private final ArrayList<String> bookmarks=new ArrayList<>(), history=new ArrayList<>();
    private String engineName="Google", engineTemplate=Router.ENGINES[0][1];
    private volatile boolean filter=true;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences("aetherbolt", MODE_PRIVATE);
        engineName=prefs.getString("engineName", "Google"); engineTemplate=prefs.getString("engineTemplate", Router.ENGINES[0][1]); filter=prefs.getBoolean("filter", true);
        readList("bookmarks",bookmarks); readList("history",history);
        build(); addTab();
    }
    private int dp(float x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private LinearLayout column(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);return c;}
    private TextView label(String text,int size,int color){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);return t;}
    private TextView button(String text,String desc,Runnable action){TextView t=label(text,17,TEXT);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(44));t.setMinWidth(dp(44));t.setPadding(dp(8),dp(6),dp(8),dp(6));t.setBackground(shape(PANEL,12,0));t.setContentDescription(desc);t.setOnClickListener(v->action.run());return t;}
    private void add(LinearLayout parent, View v,int width,int height){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height));p.setMargins(dp(3),dp(3),dp(3),dp(3));parent.addView(v,p);}
    private void build(){
        root=column();root.setBackgroundColor(BG);
        if(Build.VERSION.SDK_INT>=35) root.setOnApplyWindowInsetsListener((v,i)->{android.graphics.Insets in=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());v.setPadding(in.left,in.top,in.right,in.bottom);return i;});
        HorizontalScrollView sc=new HorizontalScrollView(this);sc.setHorizontalScrollBarEnabled(false);tabsBar=row();tabsBar.setPadding(dp(4),dp(3),dp(4),dp(3));sc.addView(tabsBar);root.addView(sc);
        toolBar=row();toolBar.setPadding(dp(3),0,dp(3),0);
        add(toolBar,button("⌂","Главная",()->{current().home=true;showTab();}),44,44);
        address=new EditText(this);address.setSingleLine(true);address.setTextSize(15);address.setTextColor(TEXT);address.setHintTextColor(MUTED);address.setHint("Введите запрос или адрес");address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);address.setImeOptions(EditorInfo.IME_ACTION_GO);address.setSelectAllOnFocus(true);address.setPadding(dp(14),0,dp(14),0);address.setBackground(shape(PANEL,23,PURPLE));
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(46),1);a.setMargins(dp(3),0,dp(3),0);toolBar.addView(address,a);
        address.setOnEditorActionListener((v,id,event)->{if(id==EditorInfo.IME_ACTION_GO||(event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER&&event.getAction()==KeyEvent.ACTION_DOWN)){navigate(address.getText().toString());return true;}return false;});
        add(toolBar,button("↻","Обновить",()->{if(!current().home)current().web.reload();}),44,44);root.addView(toolBar);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN));root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));progress.setVisibility(View.INVISIBLE);
        body=new FrameLayout(this);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout bottom=row();bottom.setPadding(dp(4),0,dp(4),dp(4));
        String[] icons={"‹","›","☆","▣","☰"}, desc={"Назад","Вперёд","Закладка","Вкладки","Меню"};
        Runnable[] actions={()->back(),()->{if(!current().home&&current().web.canGoForward())current().web.goForward();},()->saveBookmark(),()->tabDialog(),()->menu()};
        for(int i=0;i<icons.length;i++){TextView b=button(icons[i],desc[i],actions[i]);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.setMargins(dp(3),dp(3),dp(3),dp(3));bottom.addView(b,p);}root.addView(bottom);setContentView(root);
    }
    private Tab current(){return tabs.get(active);}
    private void addTab(){if(tabs.size()>=12){toast("Лимит альфа-версии: 12 вкладок");return;}Tab t=new Tab();t.web=new WebView(this);configure(t);tabs.add(t);active=tabs.size()-1;showTab();}
    private void closeTab(int index){Tab t=tabs.remove(index);if(t.web.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)t.web.getParent()).removeView(t.web);t.web.stopLoading();t.web.destroy();if(tabs.isEmpty()){active=0;addTab();return;}if(index<active)active--;else if(active>=tabs.size())active=tabs.size()-1;showTab();}
    private void refreshTabs(){tabsBar.removeAllViews();for(int i=0;i<tabs.size();i++){final int n=i;Tab t=tabs.get(i);LinearLayout chip=row();chip.setBackground(shape(i==active?0xff30304f:PANEL,12,i==active?PURPLE:0));TextView title=label(t.home?"Новая вкладка":t.title,13,TEXT);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);title.setMaxWidth(dp(130));title.setPadding(dp(10),dp(9),dp(8),dp(9));title.setOnClickListener(v->{active=n;showTab();});chip.addView(title);TextView x=button("×","Закрыть вкладку",()->closeTab(n));chip.addView(x,new LinearLayout.LayoutParams(dp(44),dp(44)));add(tabsBar,chip,-2,44);}add(tabsBar,button("+","Новая вкладка",()->addTab()),44,44);}
    private void showTab(){body.removeAllViews();Tab t=current();if(t.home){address.setText("");body.addView(home(),new FrameLayout.LayoutParams(-1,-1));}else{if(t.web.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)t.web.getParent()).removeView(t.web);body.addView(t.web,new FrameLayout.LayoutParams(-1,-1));address.setText(t.web.getUrl());}updateProgress(t);refreshTabs();address.clearFocus();}
    private void updateProgress(Tab t){if(!tabs.isEmpty()&&t==current()){progress.setProgress(t.progress);progress.setVisibility(!t.home&&t.progress>0&&t.progress<100?View.VISIBLE:View.INVISIBLE);}}
    private View home(){
        ScrollView sc=new ScrollView(this);GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff11182d,0xff251747,0xff0b1020});sc.setBackground(bg);LinearLayout box=column();box.setGravity(Gravity.CENTER_HORIZONTAL);box.setPadding(dp(18),dp(25),dp(18),dp(25));sc.addView(box,new ScrollView.LayoutParams(-1,-2));
        ImageView icon=new ImageView(this);icon.setImageResource(com.aetherbolt.browser.R.drawable.bolt);add(box,icon,84,84);
        TextView brand=label("AetherBolt Browser",28,TEXT);brand.setGravity(Gravity.CENTER);brand.setTypeface(Typeface.DEFAULT,Typeface.BOLD);add(box,brand,-1,-2);
        TextView slogan=label("Beyond the Limits.",15,PURPLE);slogan.setGravity(Gravity.CENTER);add(box,slogan,-1,-2);
        TextView status=label("ALPHA 0.1  •  Android",11,MUTED);status.setGravity(Gravity.CENTER);add(box,status,-1,26);
        TextView search=button("⌕   Поиск в интернете", "Поиск",()->{address.requestFocus();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(address,InputMethodManager.SHOW_IMPLICIT);});search.setBackground(shape(PANEL,22,PURPLE));add(box,search,-1,50);
        TextView e=label("Поисковик: "+engineName+"  ▾",14,CYAN);e.setGravity(Gravity.CENTER);e.setOnClickListener(v->engineDialog());add(box,e,-1,40);
        String[] names={"Google","DuckDuckGo","Bing","Brave Search","Qwant","Яндекс"};int cols=getResources().getConfiguration().screenWidthDp>=650?6:3;
        for(int i=0;i<names.length;i+=cols){LinearLayout r=row();for(int j=i;j<Math.min(i+cols,names.length);j++){final int n=j;TextView tile=button(names[j],names[j],()->setEngine(Router.ENGINES[n][0],Router.ENGINES[n][1]));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(60),1);p.setMargins(dp(4),dp(4),dp(4),dp(4));r.addView(tile,p);}box.addView(r,new LinearLayout.LayoutParams(-1,-2));}
        LinearLayout shortcuts=row();String[] n={"☆ Закладки","◷ История","⚙ Настройки"};Runnable[] a={()->listDialog("Закладки",bookmarks,"bookmarks"),()->listDialog("История",history,"history"),()->settings()};for(int i=0;i<n.length;i++){TextView b=button(n[i],n[i],a[i]);b.setTextSize(13);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(58),1);p.setMargins(dp(4),dp(8),dp(4),dp(4));shortcuts.addView(b,p);}box.addView(shortcuts,new LinearLayout.LayoutParams(-1,-2));
        TextView note=label("Tor ещё не подключён в этой версии.\n.onion-адреса из строки не отправляются в поиск.",12,MUTED);note.setGravity(Gravity.CENTER);add(box,note,-1,-2);return sc;
    }
    private void configure(Tab t){WebView w=t.web;WebSettings s=w.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSupportMultipleWindows(false);s.setJavaScriptCanOpenWindowsAutomatically(false);s.setMediaPlaybackRequiresUserGesture(true);s.setSafeBrowsingEnabled(true);s.setBuiltInZoomControls(true);s.setDisplayZoomControls(false);CookieManager.getInstance().setAcceptThirdPartyCookies(w,false);
        w.setWebChromeClient(new WebChromeClient(){@Override public void onProgressChanged(WebView v,int p){t.progress=p;updateProgress(t);}@Override public void onReceivedTitle(WebView v,String title){if(title!=null&&!title.trim().isEmpty()){t.title=title;refreshTabs();}}});
        w.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){Router.Target target=Router.parse(req.getUrl().toString());if(target.kind!=Router.Kind.WEB){if(req.isForMainFrame())toast(target.kind==Router.Kind.ONION?"Tor пока не встроен; адрес заблокирован":"Неподдерживаемый адрес");return true;}return false;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest req){String host=req.getUrl().getHost();if(Router.onionHost(host)||(filter&&!req.isForMainFrame()&&isAdHost(host)))return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));return null;}
            @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap favicon){if(Router.onionHost(Uri.parse(url).getHost())){v.stopLoading();toast("Tor не подключён");return;}if(t==current()&&!t.home&&!address.hasFocus())address.setText(url);}
            @Override public void onPageFinished(WebView v,String url){if(Router.parse(url).kind==Router.Kind.WEB){remember(history,"history",url,200);if(t==current()&&!t.home&&!address.hasFocus())address.setText(url);}}
        });
        w.setDownloadListener((url,agent,disposition,mime,length)->{if(Router.parse(url).kind!=Router.Kind.WEB){toast("Адрес загрузки не поддерживается");return;}new AlertDialog.Builder(this).setTitle("Загрузки пока не встроены").setMessage("Открыть ссылку во внешнем приложении? Это не приватный режим.").setNegativeButton("Отмена",null).setPositiveButton("Открыть",(d,i)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){toast("Нет подходящего приложения");}}).show();});
    }
    private boolean isAdHost(String h){if(h==null)return false;h=h.toLowerCase(Locale.ROOT);for(String domain:new String[]{"doubleclick.net","googlesyndication.com","googleadservices.com","adnxs.com","adsrvr.org"})if(h.equals(domain)||h.endsWith("."+domain))return true;return false;}
    private void navigate(String raw){Router.Target target=Router.resolve(raw,engineTemplate);if(target.kind==Router.Kind.ONION){new AlertDialog.Builder(this).setTitle("Сеть Tor").setMessage("Распознан .onion-адрес. В этой альфа-версии Tor ещё не реализован, поэтому адрес не загружен.").setPositiveButton("OK",null).show();return;}if(target.kind==Router.Kind.INVALID){toast("Введите корректный HTTP(S)-адрес или запрос");return;}((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);Tab t=current();t.home=false;showTab();address.setText(target.url);t.web.loadUrl(target.url);}
    private void back(){Tab t=current();if(!t.home&&t.web.canGoBack()){t.web.goBack();}else if(!t.home){t.home=true;showTab();}else if(tabs.size()>1){closeTab(active);}else{super.onBackPressed();}}
    @Override public void onBackPressed(){back();}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void setEngine(String name,String template){engineName=name;engineTemplate=template;prefs.edit().putString("engineName",name).putString("engineTemplate",template).apply();if(current().home)showTab();toast("Поисковик: "+name);}
    private void engineDialog(){String[] names=new String[Router.ENGINES.length+1];int checked=-1;for(int i=0;i<Router.ENGINES.length;i++){names[i]=Router.ENGINES[i][0];if(engineName.equals(names[i]))checked=i;}names[names.length-1]="Добавить свой (SearXNG и др.)";new AlertDialog.Builder(this).setTitle("Поисковая система").setSingleChoiceItems(names,checked,(d,i)->{d.dismiss();if(i<Router.ENGINES.length)setEngine(names[i],Router.ENGINES[i][1]);else customEngine();}).setNegativeButton("Закрыть",null).show();}
    private void customEngine(){LinearLayout c=column();c.setPadding(dp(20),dp(6),dp(20),0);EditText name=new EditText(this),url=new EditText(this);name.setHint("Название");url.setHint("https://example.org/search?q={query}");url.setSingleLine(true);c.addView(name);c.addView(url);AlertDialog d=new AlertDialog.Builder(this).setTitle("Свой поисковик").setView(c).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();d.setOnShowListener(v->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{String n=name.getText().toString().trim(),u=url.getText().toString().trim();if(n.isEmpty()||!Router.validTemplate(u)){url.setError("Нужен HTTP(S)-шаблон с {query}; не .onion");return;}setEngine(n,u);d.dismiss();}));d.show();}
    private void tabDialog(){String[] labels=new String[tabs.size()+1];for(int i=0;i<tabs.size();i++)labels[i]=tabs.get(i).home?"Новая вкладка":tabs.get(i).title;labels[labels.length-1]="+ Новая вкладка";new AlertDialog.Builder(this).setTitle("Вкладки ("+tabs.size()+")").setSingleChoiceItems(labels,active,(d,i)->{d.dismiss();if(i==tabs.size())addTab();else{active=i;showTab();}}).setNegativeButton("Закрыть",null).show();}
    private void menu(){String[] items={"Новая вкладка","Поисковая система","Закладки","История","Настройки","О браузере"};new AlertDialog.Builder(this).setTitle("AetherBolt ⚡").setItems(items,(d,i)->{switch(i){case 0:addTab();break;case 1:engineDialog();break;case 2:listDialog("Закладки",bookmarks,"bookmarks");break;case 3:listDialog("История",history,"history");break;case 4:settings();break;case 5:new AlertDialog.Builder(this).setTitle("AetherBolt 0.1 Alpha").setMessage("Экспериментальный браузер на Android WebView. Tor, расширения, приватные профили и встроенные загрузки пока не реализованы. Не использовать для анонимного доступа.").setPositiveButton("OK",null).show();}}).show();}
    private void settings(){String[] items={"Поисковая система: "+engineName,"Базовый фильтр рекламы: "+(filter?"вкл":"выкл"),"Tor: не реализован","Очистить данные сайтов и историю"};new AlertDialog.Builder(this).setTitle("Настройки").setItems(items,(d,i)->{switch(i){case 0:engineDialog();break;case 1:filter=!filter;prefs.edit().putBoolean("filter",filter).apply();settings();break;case 2:new AlertDialog.Builder(this).setTitle("Tor").setMessage("Подключение ещё не реализовано. В будущей версии .onion должен открываться автоматически в изолированном сетевом профиле.").setPositiveButton("OK",null).show();break;case 3:clearData();}}).show();}
    private void clearData(){new AlertDialog.Builder(this).setTitle("Очистить данные?").setMessage("Все открытые вкладки будут закрыты, история, cookies и данные сайтов удалены. Закладки сохранятся.").setNegativeButton("Отмена",null).setPositiveButton("Очистить",(d,i)->{body.removeAllViews();for(Tab t:tabs){t.web.stopLoading();t.web.clearHistory();t.web.clearCache(true);t.web.destroy();}tabs.clear();history.clear();writeList("history",history);WebStorage.getInstance().deleteAllData();CookieManager.getInstance().removeAllCookies(ok->CookieManager.getInstance().flush());active=0;addTab();}).show();}
    private void readList(String key,ArrayList<String> out){try{JSONArray a=new JSONArray(prefs.getString(key,"[]"));for(int i=0;i<a.length();i++)out.add(a.getString(i));}catch(JSONException ignored){}}
    private void writeList(String key,ArrayList<String> data){prefs.edit().putString(key,new JSONArray(data).toString()).apply();}
    private void remember(ArrayList<String> data,String key,String url,int cap){data.remove(url);data.add(0,url);while(data.size()>cap)data.remove(data.size()-1);writeList(key,data);}
    private void saveBookmark(){Tab t=current();String u=t.web.getUrl();if(t.home||u==null||Router.parse(u).kind!=Router.Kind.WEB){toast("Сначала откройте сайт");return;}remember(bookmarks,"bookmarks",u,200);toast("Закладка сохранена");}
    private void listDialog(String title,ArrayList<String> data,String key){String[] snapshot=data.toArray(new String[0]);AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(title).setNegativeButton("Закрыть",null);if(snapshot.length==0)b.setMessage("Пока пусто");else b.setItems(snapshot,(d,i)->navigate(snapshot[i])).setNeutralButton("Очистить список",(d,i)->new AlertDialog.Builder(this).setTitle("Удалить весь список?").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(dd,j)->{data.clear();writeList(key,data);}).show());b.show();}
    @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);showTab();}
    @Override protected void onDestroy(){if(body!=null)body.removeAllViews();for(Tab t:tabs){t.web.stopLoading();t.web.destroy();}super.onDestroy();}
}
