package com.aetherbolt.browser;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Pure Java address classification. This is not a network security boundary. */
public final class Router {
    public static final String[][] ENGINES = {
        {"Google", "https://www.google.com/search?q={query}"},
        {"DuckDuckGo", "https://duckduckgo.com/?q={query}"},
        {"Bing", "https://www.bing.com/search?q={query}"},
        {"Brave Search", "https://search.brave.com/search?q={query}"},
        {"Qwant", "https://www.qwant.com/?q={query}"},
        {"Яндекс", "https://yandex.com/search/?text={query}"},
        {"Yahoo", "https://search.yahoo.com/search?p={query}"},
        {"Startpage", "https://www.startpage.com/sp/search?query={query}"},
        {"Ecosia", "https://www.ecosia.org/search?q={query}"},
        {"Mojeek", "https://www.mojeek.com/search?q={query}"},
        {"Ahmia", "https://ahmia.fi/search/?q={query}"},
        {"Swisscows", "https://swisscows.com/en/web?query={query}"}
    };
    public enum Kind { WEB, ONION, INVALID }
    public static final class Target {
        public final Kind kind; public final String url;
        Target(Kind kind, String url) { this.kind=kind; this.url=url; }
    }
    public static boolean onionHost(String host) {
        if (host == null) return false;
        String h=host.toLowerCase(Locale.ROOT).replaceAll("\\.+$", "");
        return h.equals("onion") || h.endsWith(".onion");
    }
    public static boolean validTemplate(String template) {
        if (template == null || !template.contains("{query}")) return false;
        return parse(template.replace("{query}", "test")).kind == Kind.WEB;
    }
    public static String search(String query, String template) {
        if (!validTemplate(template)) template=ENGINES[0][1];
        try { return template.replace("{query}", URLEncoder.encode(query, "UTF-8")); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }
    public static Target parse(String url) {
        try {
            URI uri = new URI(url);
            String scheme=uri.getScheme(), host=uri.getHost();
            if (scheme==null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) || host==null || uri.getUserInfo()!=null) return new Target(Kind.INVALID, "");
            return new Target(onionHost(host)?Kind.ONION:Kind.WEB, url);
        } catch (Exception e) { return new Target(Kind.INVALID, ""); }
    }
    public static Target resolve(String raw, String template) {
        String s=raw.trim();
        if(s.isEmpty()) return new Target(Kind.INVALID, "");
        if (s.matches("(?is)^(https?)://.*")) return parse(s);
        if (s.matches("(?is)^[a-z][a-z0-9+.-]*:.*") && !s.matches("^[^ /]+\\.[^ /]+:\\d+.*")) return new Target(Kind.INVALID, "");
        if (!s.matches("(?s).*\\s.*") && (s.contains(".") || s.startsWith("localhost"))) {
            Target t=parse("https://"+s); if(t.kind!=Kind.INVALID) return t;
            if (s.toLowerCase(Locale.ROOT).contains(".onion")) return new Target(Kind.INVALID, "");
        }
        // Avoid forwarding a potentially sensitive malformed onion address to a public search provider.
        if (s.toLowerCase(Locale.ROOT).contains(".onion")) return new Target(Kind.INVALID, "");
        return parse(search(s, template));
    }
    private Router() {}
}
