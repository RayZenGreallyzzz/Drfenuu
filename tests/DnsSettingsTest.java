import com.aetherbolt.browser.DnsSettings;
public class DnsSettingsTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        for(String[] provider:DnsSettings.PROVIDERS)check(DnsSettings.validEndpoint(provider[1]));
        check(DnsSettings.validMode("system"));check(DnsSettings.validMode("fallback"));check(DnsSettings.validMode("strict"));check(!DnsSettings.validMode("other"));
        check(DnsSettings.validEndpoint("https://resolver.example/dns-query"));
        for(String invalid:new String[]{"http://dns.example/dns-query","https://user:pass@dns.example/dns-query","https://dns.example/dns-query#fragment","https://hidden.onion/dns-query","https://dns.example:70000/","https://","file:///dns","https://dns.example:0/"})check(!DnsSettings.validEndpoint(invalid));
        System.out.println("DnsSettingsTest passed");
    }
}
