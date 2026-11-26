package com.aetherbolt.browser;
import android.content.Context;
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
    void restore(Done done){if(ready()||!file().isFile()){done.finish(null);return;}worker.execute(()->{try{config=parse(read(new FileInputStream(file())));done.finish(null);}catch(Exception e){done.finish("Сохранённый профиль не читается");}});}
    private String read(InputStream input)throws IOException{if(input==null)throw new IOException("Нет файла");try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>65536)throw new IOException("Лимит 64 КБ");out.write(b,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    void importProfile(InputStream in,Done done){if(busy||active()){try{if(in!=null)in.close();}catch(IOException ignored){}done.finish("Сначала отключи VPN");return;}busy=true;worker.execute(()->{String error=null;try{String raw=read(in);Config parsed=parse(raw);android.util.AtomicFile af=new android.util.AtomicFile(file());FileOutputStream out=af.startWrite();try{out.write(raw.getBytes(StandardCharsets.UTF_8));af.finishWrite(out);}catch(Exception e){af.failWrite(out);throw e;}config=parsed;}catch(Exception e){error=e instanceof IOException?e.getMessage():"Некорректная конфигурация WireGuard";}finally{busy=false;}done.finish(error);});}
    void change(boolean up,Done done){if(busy){done.finish("Подключение уже обрабатывается");return;}if(up&&!ready()){done.finish("Импортируй профиль .conf");return;}busy=true;worker.execute(()->{String error=null;try{if(backend==null)backend=new GoBackend(context);state=backend.setState(tunnel,up?Tunnel.State.UP:Tunnel.State.DOWN,up?config:null);}catch(Exception|LinkageError e){error="Не удалось изменить состояние WireGuard. Проверь профиль и системное разрешение VPN.";}finally{busy=false;}done.finish(error);});}
    void forget(Done done){if(busy||active()){done.finish("Сначала отключи VPN");return;}worker.execute(()->{if(file().exists()&&!file().delete()){done.finish("Не удалось удалить профиль");return;}config=null;done.finish(null);});}
}
