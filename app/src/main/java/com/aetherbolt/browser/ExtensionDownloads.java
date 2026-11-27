package com.aetherbolt.browser;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.text.InputFilter;
import android.widget.*;
import java.util.Locale;
import org.mozilla.geckoview.*;

/** User-confirmed WebExtensions downloads.download() adapter for the browser's native queue. */
final class ExtensionDownloads implements WebExtension.DownloadDelegate {
    private static final int MAX_DIALOGS=2;
    private final MainActivity ui;
    private final FileTransfers transfers;
    private final GeckoWebExecutor executor;
    private final WebExtensionController controller;
    private final SharedPreferences prefs;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean destroyed;
    private int pending;
    ExtensionDownloads(MainActivity ui,GeckoRuntime runtime,FileTransfers transfers){
        this.ui=ui;this.transfers=transfers;controller=runtime.getWebExtensionController();
        executor=new GeckoWebExecutor(runtime);prefs=ui.getSharedPreferences("aetherbolt",Context.MODE_PRIVATE);
    }
    private void reject(GeckoResult<WebExtension.DownloadInitData> r,String reason){r.completeExceptionally(new IllegalStateException(reason));}
    private static String safeName(String file,String url){
        String name=file==null?"":file.replace('\\','/');int p=name.lastIndexOf('/');if(p>=0)name=name.substring(p+1);
        if(name.isEmpty()){
            Uri u=Uri.parse(url);String path=u.getLastPathSegment();name=path==null?"extension-download":path;
        }
        name=name.replaceAll("[\\p{Cntrl}/\\\\:*?\\\"<>|]","_").trim();
        if(name.isEmpty()||name.equals(".")||name.equals(".."))name="extension-download";
        if(name.length()>120)name=name.substring(name.length()-120);
        return name;
    }
    @Override public GeckoResult<WebExtension.DownloadInitData> onDownload(WebExtension source,WebExtension.DownloadRequest request){
        GeckoResult<WebExtension.DownloadInitData> result=new GeckoResult<>();
        ui.runOnUiThread(()->{
            if(destroyed||!ui.canPromptExtensionDownload()){reject(result,"Браузер неактивен. Повторите загрузку из расширения.");return;}
            if(pending>=MAX_DIALOGS||transfers.activeCount()>=12){reject(result,"Занята очередь загрузок.");return;}
            if(source==null||source.metaData==null||!source.metaData.enabled||request==null||request.request==null){reject(result,"Расширение или запрос недоступны.");return;}
            String target=request.request.uri;
            Uri u;
            try{u=Uri.parse(target);}catch(Exception ex){reject(result,"Некорректный URL.");return;}
            String scheme=u.getScheme();
            if(u.getHost()==null||(!"https".equalsIgnoreCase(scheme)&&!"http".equalsIgnoreCase(scheme))||!"GET".equalsIgnoreCase(request.request.method)){
                reject(result,"Поддерживаются только HTTP(S) GET. Blob/data URL пока не поддерживаются.");return;
            }
            String filename=safeName(request.filename,target),host=u.getHost();
            if(filename.length()>120){reject(result,"Имя файла слишком длинное");return;}
            LinearLayout box=new LinearLayout(ui);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(24,10,24,10);
            TextView info=new TextView(ui);info.setText("Расширение: "+source.metaData.name+"\nСайт: "+host+"\nHTTP(S) GET • файл будет сохранён в Download/AetherBolt");box.addView(info);
            EditText name=new EditText(ui);name.setSingleLine(true);name.setText(filename);name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(120)});name.setSelectAllOnFocus(true);box.addView(name);
            final boolean[] completed={false};pending++;
            AlertDialog d=new AlertDialog.Builder(ui).setTitle("Скачать файл из расширения?").setView(box)
                .setPositiveButton("Скачать",(dialog,which)->{
                    completed[0]=true;
                    String chosen=safeName(name.getText().toString(),"https://example.invalid/download");
                    fetch(request,chosen,result);
                }).setNegativeButton("Отмена",(dialog,which)->{completed[0]=true;reject(result,"Загрузка отменена.");}).create();
            d.setOnDismissListener(dialog->{pending=Math.max(0,pending-1);if(!completed[0])reject(result,"Загрузка отменена.");});
            d.show();
        });
        return result;
    }
    private void fetch(WebExtension.DownloadRequest req,String filename,GeckoResult<WebExtension.DownloadInitData> result){
        if(destroyed||ui.isDestroyed()){reject(result,"Браузер закрыт.");return;}
        try{
            executor.fetch(req.request,req.downloadFlags).then(response->{
                ui.runOnUiThread(()->{
                    if(destroyed||ui.isDestroyed()){if(response!=null&&response.body!=null)try{response.body.close();}catch(Exception ignored){}reject(result,"Браузер закрыт.");return;}
                    if(response==null||response.body==null){reject(result,"Сервер не вернул содержимое.");return;}
                    if(!req.allowHttpErrors&&response.statusCode>=400){try{response.body.close();}catch(Exception ignored){}reject(result,"Сервер вернул HTTP "+response.statusCode);return;}
                    try{
                        String type=response.headers.get("content-type");if(type==null||type.isEmpty())type="application/octet-stream";
                        int id=Math.max(1,prefs.getInt("extDownloadNextId",1));
                        prefs.edit().putInt("extDownloadNextId",id==Integer.MAX_VALUE?1:id+1).apply();
                        WebExtension.Download download=controller.createDownload(id);
                        if(download==null){response.body.close();reject(result,"Движок не создал запись загрузки.");return;}
                        FileTransfers.Job job=transfers.enqueueExtensionResponse(response,filename,type.split(";",2)[0]);
                        if(job==null){reject(result,"Очередь загрузок заполнена.");return;}
                        final String mime=type.split(";",2)[0],name=job.name;
                        final WebExtension.Download.Info info=new WebExtension.Download.Info(){
                            @Override public String filename(){return name;}
                            @Override public String mime(){return mime;}
                            @Override public long startTime(){return job.started;}
                            @Override public long totalBytes(){return job.total;}
                            @Override public long bytesReceived(){return job.bytes;}
                            @Override public long fileSize(){return job.done&&job.completed?job.bytes:-1;}
                            @Override public boolean fileExists(){return job.done&&job.completed;}
                            @Override public boolean paused(){return job.paused;}
                            @Override public int state(){return job.done?(job.completed?WebExtension.Download.STATE_COMPLETE:WebExtension.Download.STATE_INTERRUPTED):WebExtension.Download.STATE_IN_PROGRESS;}
                            @Override public Integer error(){return !job.done||job.completed?null:job.cancelled?WebExtension.Download.INTERRUPT_REASON_USER_CANCELED:WebExtension.Download.INTERRUPT_REASON_FILE_FAILED;}
                            @Override public Long endTime(){return job.done?System.currentTimeMillis():null;}
                        };
                        result.complete(new WebExtension.DownloadInitData(download,info));
                        tick(download,job,info);
                    }catch(Exception e){try{response.body.close();}catch(Exception ignored){}reject(result,"Невозможно создать загрузку.");}
                });return null;
            },error->{ui.runOnUiThread(()->{if(!destroyed)reject(result,"Сетевая ошибка загрузки.");});return null;});
        }catch(Exception e){reject(result,"Ошибка запроса расширения.");}
    }
    private void tick(WebExtension.Download download,FileTransfers.Job job,WebExtension.Download.Info info){
        if(destroyed||ui.isDestroyed())return;
        download.update(info);
        if(!job.done)handler.postDelayed(()->tick(download,job,info),1000);
    }
    void destroy(){destroyed=true;handler.removeCallbacksAndMessages(null);}
}
