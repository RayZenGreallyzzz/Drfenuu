package com.aetherbolt.browser;
import java.net.URI;

public final class DnsSettings {
    private DnsSettings(){}
    public static final String[][] PROVIDERS={
        {"Cloudflare","https://cloudflare-dns.com/dns-query"},
        {"Quad9","https://dns.quad9.net/dns-query"},
        {"Google","https://dns.google/dns-query"},
        {"AdGuard DNS","https://dns.adguard-dns.com/dns-query"}
    };
    public static boolean validMode(String mode){
        return "system".equals(mode)||"fallback".equals(mode)||"strict".equals(mode);
    }
    public static boolean validEndpoint(String raw){
        if(raw==null||raw.length()>2048)return false;
        try{
            URI u=new URI(raw);
            return "https".equalsIgnoreCase(u.getScheme())&&u.getHost()!=null&&!Router.onionHost(u.getHost())
                &&u.getRawUserInfo()==null&&u.getRawFragment()==null&&!u.isOpaque()
                &&u.getPort()!=0&&u.getPort()<=65535;
        }catch(Exception e){return false;}
    }
}
