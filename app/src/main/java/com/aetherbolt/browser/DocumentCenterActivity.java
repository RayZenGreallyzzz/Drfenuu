package com.aetherbolt.browser;
import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

public final class DocumentCenterActivity extends Activity {
    private static final int EXPORT=950,MAX_TEXT=256*1024,MAX_EXTRACT=64*1024*1024;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private Uri uri;private String name,mime,mode;
    private LinearLayout root,content,nav;
    private TextView status,heading;
    private int page,count;
    private boolean busy;
    private int archiveIndex=-1;
    private final ArrayList<String> archiveNames=new ArrayList<>();
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String t,int size){TextView v=new TextView(this);v.setText(t);v.setTextSize(size);v.setTextColor(0xffedf2ff);v.setPadding(dp(8),dp(8),dp(8),dp(8));return v;}
    private void button(LinearLayout r,String title,Runnable fn){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setOnClickListener(v->{if(!busy)fn.run();});r.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);uri=getIntent().getData();if(uri==null||!"content".equals(uri.getScheme())){finish();return;}
        mime=getIntent().getType();name=getIntent().getStringExtra("title");if(name==null)try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}catch(Exception ignored){}if(name==null)name="Документ";
        root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(10),dp(6),dp(10),dp(6));root.setBackgroundColor(0xff0b1020);
        heading=text(name,20);root.addView(heading);LinearLayout tools=new LinearLayout(this);button(tools,"Назад",this::finish);button(tools,"Текст",()->load("text"));button(tools,"HEX",()->load("hex"));button(tools,"Внешний",this::external);root.addView(tools);
        status=text("",13);root.addView(status);nav=new LinearLayout(this);root.addView(nav);
        ScrollView scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(1);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        String lower=name.toLowerCase(Locale.ROOT);
        if(lower.endsWith(".pdf")||"application/pdf".equals(mime))load("pdf");
        else if(lower.endsWith(".zip")||"application/zip".equals(mime))load("zip");
        else if(lower.endsWith(".gz")||"application/gzip".equals(mime))load("gzip");
        else if((mime!=null&&mime.startsWith("text/"))||lower.matches(".*\\.(txt|md|json|xml|html|css|js|java|py|csv|log|yaml|yml|ini|conf)$"))load("text");
        else load("hex");
    }
    private InputStream input()throws IOException{InputStream in=getContentResolver().openInputStream(uri);if(in==null)throw new IOException("Нет доступа к файлу");return in;}
    interface Work{Object run()throws Exception;}
    interface Display{void run(Object o);}
    private void run(Work work,Display display){
        if(busy)return;busy=true;status.setText("Чтение…");
        worker.execute(()->{Object result=null;String error=null;try{result=work.run();}catch(Exception e){error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
            Object value=result;String message=error;runOnUiThread(()->{if(isDestroyed()){if(value instanceof Bitmap)((Bitmap)value).recycle();return;}busy=false;if(message!=null)status.setText("Ошибка: "+message);else display.run(value);});});
    }
    private void load(String kind){
        mode=kind;nav.removeAllViews();content.removeAllViews();archiveIndex=-1;
        if("pdf".equals(kind)){
            button(nav,"‹",()->{if(page>0){page--;renderPdf();}});button(nav,"›",()->{if(page+1<count){page++;renderPdf();}});renderPdf();
        }else if("zip".equals(kind)){
            run(this::zipList,o->{archiveNames.clear();archiveNames.addAll((List<String>)o);status.setText("ZIP: "+archiveNames.size()+" записей (до 500)");for(int i=0;i<archiveNames.size();i++){int index=i;TextView v=text(archiveNames.get(i),15);v.setOnClickListener(w->chooseEntry(index));content.addView(v);}});
        }else if("gzip".equals(kind)){
            button(nav,"Распаковать",()->export(-2,name.replaceFirst("(?i)\\.gz$","")));
            run(()->{try(InputStream raw=input();GZIPInputStream gz=new GZIPInputStream(raw)){return preview(gz,4096);}},o->showText((byte[])o,false,"GZIP • первые 4 КБ"));
        }else run(()->{try(InputStream in=input()){return preview(in,"hex".equals(kind)?4096:MAX_TEXT);}},o->showText((byte[])o,"hex".equals(kind),kind.equals("hex")?"HEX • до 4 КБ":"UTF-8 • до 256 КБ"));
    }
    private static byte[] preview(InputStream in,int max)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];
        while(out.size()<max){int n=in.read(b,0,Math.min(b.length,max-out.size()));if(n<0)break;out.write(b,0,n);}return out.toByteArray();
    }
    private void showText(byte[] bytes,boolean hex,String title){
        String value;if(hex){StringBuilder b=new StringBuilder();for(int i=0;i<bytes.length;i+=16){b.append(String.format(Locale.ROOT,"%08X  ",i));for(int j=0;j<16;j++)b.append(i+j<bytes.length?String.format(Locale.ROOT,"%02X ",bytes[i+j]&255):"   ");b.append(" ");for(int j=i;j<Math.min(i+16,bytes.length);j++)b.append(bytes[j]>=32&&bytes[j]<127?(char)bytes[j]:'.');b.append('\n');}value=b.toString();}else value=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
        TextView v=text(value,13);v.setTypeface(Typeface.MONOSPACE);v.setTextIsSelectable(true);content.removeAllViews();content.addView(v);status.setText(title+" • "+bytes.length+" байт");
    }
    private void renderPdf(){
        final int requested=page;
        run(()->{
            ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(uri,"r");if(fd==null)throw new IOException("Нет доступа");
            PdfRenderer renderer;try{renderer=new PdfRenderer(fd);}catch(Exception e){fd.close();throw e;}
            try(PdfRenderer r=renderer){int pages=r.getPageCount();if(pages<1)throw new IOException("Нет страниц");try(PdfRenderer.Page p=r.openPage(Math.min(requested,pages-1))){float scale=Math.min(2f,Math.min(1800f/p.getWidth(),2200f/p.getHeight()));Bitmap bmp=Bitmap.createBitmap(Math.max(1,(int)(p.getWidth()*scale)),Math.max(1,(int)(p.getHeight()*scale)),Bitmap.Config.ARGB_8888);bmp.eraseColor(Color.WHITE);p.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);return new Object[]{bmp,pages};}}
        },o->{Object[] a=(Object[])o;count=(Integer)a[1];ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setImageBitmap((Bitmap)a[0]);content.removeAllViews();content.addView(image,new LinearLayout.LayoutParams(-1,-2));status.setText("PDF • страница "+(page+1)+" / "+count);});
    }
    private List<String> zipList()throws IOException{
        ArrayList<String> result=new ArrayList<>();long scanned=0;byte[] b=new byte[8192];
        try(InputStream raw=input();ZipInputStream zip=new ZipInputStream(raw)){
            ZipEntry e;while(result.size()<500&&(e=zip.getNextEntry())!=null){
                result.add(e.getName()+(e.isDirectory()?" [папка]":""));
                int n;while((n=zip.read(b))!=-1){scanned+=n;if(scanned>MAX_EXTRACT)throw new IOException("ZIP слишком велик для безопасного потокового просмотра (64 МБ)");if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();}
                zip.closeEntry();
            }
        }return result;
    }
    private void chooseEntry(int index){
        if(busy||index<0||index>=archiveNames.size())return;String raw=archiveNames.get(index);if(raw.endsWith(" [папка]"))return;
        String n=raw.replace('\\','/');n=n.substring(n.lastIndexOf('/')+1);if(!FileCenterActivity.validName(n)){status.setText("Недопустимое имя записи");return;}
        final String filename=n;new AlertDialog.Builder(this).setTitle(raw).setMessage("Извлечь один файл (до 64 МБ) в выбранное место?").setNegativeButton("Отмена",null).setPositiveButton("Извлечь",(d,w)->export(index,filename)).show();
    }
    private void export(int index,String filename){
        if(!FileCenterActivity.validName(filename)){status.setText("Недопустимое имя");return;}archiveIndex=index;
        try{startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/octet-stream").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,filename),EXPORT);}catch(Exception e){archiveIndex=-1;status.setText("Не удалось выбрать файл");}
    }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);if(req!=EXPORT)return;int index=archiveIndex;archiveIndex=-1;
        if(result!=RESULT_OK||data==null||data.getData()==null||index==-1)return;Uri target=data.getData();if(uri.equals(target)){status.setText("Нельзя перезаписать исходный архив");return;}
        run(()->{
            try(OutputStream out=getContentResolver().openOutputStream(target,"w");InputStream raw=input()){
                if(out==null)throw new IOException("Нет доступа");
                if(index==-2){try(GZIPInputStream gz=new GZIPInputStream(raw)){limited(gz,out,MAX_EXTRACT);}}
                else try(ZipInputStream zip=new ZipInputStream(raw)){
                    long remaining=MAX_EXTRACT;ZipEntry e=null;for(int i=0;i<=index;i++){e=zip.getNextEntry();if(e==null)throw new IOException("Запись не найдена");if(i<index){remaining-=limited(zip,null,remaining);zip.closeEntry();}}
                    if(e==null||e.isDirectory())throw new IOException("Не файл");limited(zip,out,remaining);
                }
            }catch(Exception e){throw new IOException("Извлечение не завершено; удалите неполный файл",e);}return "Файл извлечён";
        },o->status.setText((String)o));
    }
    private static long limited(InputStream in,OutputStream out,long limit)throws IOException{
        long total=0;byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){total+=n;if(total>limit)throw new IOException("Превышен лимит распаковки");if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();if(out!=null)out.write(b,0,n);}return total;
    }
    private void external(){try{startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime==null?"*/*":mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"Открыть документ"));}catch(Exception e){status.setText("Нет подходящего приложения");}}
    @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
