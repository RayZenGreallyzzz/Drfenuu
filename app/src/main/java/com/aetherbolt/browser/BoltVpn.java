package com.aetherbolt.browser;
import android.content.Context;
import android.net.Uri;
import java.util.*;
import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;
import com.wireguard.config.InetNetwork;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class BoltVpn {
    private static BoltVpn instance;
    static synchronized BoltVpn get(Context c){if(instance==null)instance=new BoltVpn(c.getApplicationContext());return instance;}
    private final Context context;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private GoBackend backend;
    private volatile Config config;
    private volatile Tunnel.State state=Tunnel.State.DOWN;
    private volatile boolean busy;
    private final Tunnel tunnel=new Tunnel(){
        @Override public String getName(){return "AetherBolt";}
        @Override public void onStateChange(State newState){state=newState;}
    };
    interface Done{void finish(String error);}
    private BoltVpn(Context c){context=c;}
    boolean ready(){return config!=null;}
    boolean active(){return state==Tunnel.State.UP;}
    boolean busy(){return busy;}
    private File file(){return new File(context.getNoBackupFilesDir(),"aether-vpn.conf");}
    private File folder(){return new File(context.getNoBackupFilesDir(),"vpn-profiles");}
    private final android.content.SharedPreferences selection(){return context.getSharedPreferences("vpn-selection",Context.MODE_PRIVATE);}
    private volatile String selected="";
    private volatile List<String> names=Collections.emptyList();
    List<String> profiles(){return new ArrayList<>(names);}
    String selected(){return selected;}
    private void refresh(){String[] a=folder().list((d,n)->n.matches("[A-Za-z0-9._ -]{1,100}\\.conf"));if(a==null)a=new String[0];Arrays.sort(a,String.CASE_INSENSITIVE_ORDER);names=Collections.unmodifiableList(Arrays.asList(a));}
    private void store(File file,String raw)throws Exception{android.util.AtomicFile af=new android.util.AtomicFile(file);FileOutputStream out=af.startWrite();try{out.write(raw.getBytes(StandardCharsets.UTF_8));af.finishWrite(out);}catch(Exception e){af.failWrite(out);throw e;}}
    private void selectInternal(String name)throws Exception{if(!names.contains(name))throw new IOException("Профиль не найден");Config next=parse(read(new FileInputStream(new File(folder(),name))));config=next;selected=name;selection().edit().putString("selected",name).apply();}
    private String displayName(Uri uri){String name="";try(android.database.Cursor c=context.getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}catch(Exception ignored){}return name==null?"":name;}
    private String profileName(String raw,String name){String safe=name.replaceAll("(?i)\\.txt$","").replaceAll("(?i)\\.conf$","").replaceAll("[^A-Za-z0-9._ -]","_").trim();if(safe.isEmpty()||safe.equals(".")||safe.equals(".."))safe="WireGuard";if(safe.length()>70)safe=safe.substring(0,70);return safe+".conf";}
    private int importOne(String raw,String filename)throws Exception{
        parse(raw);String name=profileName(raw,filename);File dst=new File(folder(),name);int i=2;
        while(dst.exists()){if(raw.equals(read(new FileInputStream(dst))))return 0;dst=new File(folder(),name.substring(0,name.length()-5)+" ("+(i++)+").conf");}
        if(names.size()>=500)throw new IOException("Лимит профилей");store(dst,raw);refresh();return 1;
    }
    private String readEntry(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>65536)throw new IOException("Лимит 64 КБ");out.write(b,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);}
    void importMany(List<Uri> uris,Done done){
        if(busy||active()){done.finish("Сначала отключи VPN");return;}if(uris.isEmpty()||uris.size()>100){done.finish("Выбери от 1 до 100 файлов");return;}busy=true;
        worker.execute(()->{int added=0,duplicates=0,failed=0,entries=0;try{
            if(!folder().isDirectory()&&!folder().mkdirs())throw new IOException("Нет папки профилей");refresh();
            for(Uri uri:uris){try{
                String name=displayName(uri);if(name.toLowerCase(Locale.ROOT).endsWith(".zip")){
                    try(InputStream raw=context.getContentResolver().openInputStream(uri);java.util.zip.ZipInputStream z=new java.util.zip.ZipInputStream(raw)){
                        java.util.zip.ZipEntry e;while((e=z.getNextEntry())!=null){
                            if(++entries>100)throw new IOException("Лимит 100 профилей");
                            String entry=e.getName(),lower=entry.toLowerCase(Locale.ROOT);
                            if(e.isDirectory()||entry.contains("/")||entry.contains("\\")||(!lower.endsWith(".conf")&&!lower.endsWith(".conf.txt")))throw new IOException("ZIP должен содержать только .conf в корне");
                            String cfg=readEntry(z);try{if(importOne(cfg,entry)==1)added++;else duplicates++;}catch(Exception ex){failed++;}z.closeEntry();
                        }
                    }
                }else{if(++entries>100)throw new IOException("Лимит 100 профилей");if(importOne(read(context.getContentResolver().openInputStream(uri)),name)==1)added++;else duplicates++;}
            }catch(Exception ex){failed++;}if(entries>=100)break;}
            refresh();if(!ready()&&!names.isEmpty())selectInternal(names.get(0));
        }catch(Exception e){failed++;}finally{busy=false;}done.finish("Добавлено: "+added+" • Повторы: "+duplicates+" • Ошибки: "+failed);});
    }
    void select(String name,Done done){if(busy||active()){done.finish("Отключи VPN перед выбором сервера");return;}busy=true;worker.execute(()->{String err=null;try{selectInternal(name);}catch(Exception e){err="Не удалось выбрать профиль";}finally{busy=false;}done.finish(err);});}
    private Config parse(String raw)throws Exception{
        if(raw.length()>65536)throw new IOException("Конфигурация слишком большая");
        StringBuilder clean=new StringBuilder();boolean found=false;
        for(String line:raw.split("\\r?\\n")){
            String trim=line.split("#",2)[0].trim();
            if(trim.matches("(?i)^(IncludedApplications|ExcludedApplications)\\s*=.*"))throw new IOException("Удали ограничения приложений из исходного профиля");
            clean.append(line).append('\n');
            if(trim.equalsIgnoreCase("[Interface]")){if(found)throw new IOException("Повтор Interface");found=true;clean.append("IncludedApplications = ").append(context.getPackageName()).append('\n');}
        }
        if(!found)throw new IOException("Нет Interface");
        Config c=Config.parse(new ByteArrayInputStream(clean.toString().getBytes(StandardCharsets.UTF_8)));
        if(c.getPeers().size()!=1||c.getInterface().getDnsServers().isEmpty()||c.getInterface().getAddresses().isEmpty())throw new IOException("Нужен один Peer, Address и DNS");
        if(!c.getPeers().get(0).getEndpoint().isPresent())throw new IOException("Нет Endpoint");
        boolean v4=false,v6=false;
        for(InetNetwork ip:c.getPeers().get(0).getAllowedIps())if(ip.getMask()==0){if(ip.getAddress() instanceof java.net.Inet4Address)v4=true;else if(ip.getAddress() instanceof java.net.Inet6Address)v6=true;}
        if(!v4||!v6)throw new IOException("Нужен полный туннель: AllowedIPs = 0.0.0.0/0, ::/0");
        return c;
    }
    void restore(Done done){if(busy||ready()){done.finish(null);return;}busy=true;worker.execute(()->{String err=null;try{if(!folder().isDirectory()&&!folder().mkdirs())throw new IOException("Нет папки");if(file().isFile()){String raw=read(new FileInputStream(file()));parse(raw);File dst=new File(folder(),"Imported-legacy.conf");if(!dst.exists())store(dst,raw);if(raw.equals(read(new FileInputStream(dst))))file().delete();}refresh();String saved=selection().getString("selected","");if(names.contains(saved))selectInternal(saved);else if(!names.isEmpty())selectInternal(names.get(0));}catch(Exception e){err="Не удалось восстановить профили";}finally{busy=false;}done.finish(err);});}
    private String read(InputStream input)throws IOException{if(input==null)throw new IOException("Нет файла");try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>65536)throw new IOException("Лимит 64 КБ");out.write(b,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    void change(boolean up,Done done){if(busy){done.finish("Подключение уже обрабатывается");return;}if(up&&!ready()){done.finish("Импортируй профиль .conf");return;}busy=true;worker.execute(()->{String error=null;try{if(backend==null)backend=new GoBackend(context);state=backend.setState(tunnel,up?Tunnel.State.UP:Tunnel.State.DOWN,up?config:null);}catch(Exception|LinkageError e){error="Не удалось изменить состояние WireGuard. Проверь профиль и системное разрешение VPN.";}finally{busy=false;}done.finish(error);});}
    void forget(Done done){if(busy||active()){done.finish("Сначала отключи VPN");return;}busy=true;worker.execute(()->{String err=null;try{if(!selected.isEmpty()&&names.contains(selected)){File f=new File(folder(),selected);if(f.exists()&&!f.delete())throw new IOException("Не удалось удалить");}config=null;selected="";selection().edit().remove("selected").apply();refresh();if(!names.isEmpty())selectInternal(names.get(0));}catch(Exception e){err="Не удалось удалить профиль";}finally{busy=false;}done.finish(err);});}
}
