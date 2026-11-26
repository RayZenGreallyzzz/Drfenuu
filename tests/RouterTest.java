import com.aetherbolt.browser.Router;
public class RouterTest {
  static int count=0;
  static void check(boolean condition,String message){count++;if(!condition)throw new AssertionError(message);}
  public static void main(String[] args){
    String g=Router.ENGINES[0][1];
    check(Router.resolve("example.org",g).url.equals("https://example.org"),"normal domain");
    check(Router.resolve("https://example.org/x",g).kind==Router.Kind.WEB,"normal URL");
    check(Router.resolve("hello world",g).url.contains("hello+world"),"search encoding");
    check(Router.resolve("тест & +",g).url.contains("%26+%2B"),"unicode search encoding");
    check(Router.resolve("example.onion",g).kind==Router.Kind.ONION,"onion bare");
    check(Router.resolve("http://EXAMPLE.ONION/path",g).kind==Router.Kind.ONION,"onion case");
    check(Router.resolve("https://sub.example.onion./",g).kind==Router.Kind.ONION,"onion trailing dot");
    check(Router.resolve("https://example.onion.evil.com",g).kind==Router.Kind.WEB,"not onion");
    check(Router.resolve("bad .onion address",g).kind==Router.Kind.INVALID,"no search fallback");
    check(Router.resolve("https://user@example.onion",g).kind==Router.Kind.INVALID,"userinfo blocked");
    check(Router.resolve("javascript:alert(1)",g).kind==Router.Kind.INVALID,"scheme blocked");
    check(Router.resolve("file:///sdcard/x",g).kind==Router.Kind.INVALID,"file blocked");
    check(Router.resolve("",g).kind==Router.Kind.INVALID,"empty");
    check(Router.validTemplate("https://example.org/search?q={query}"),"custom engine");
    check(!Router.validTemplate("https://example.onion/?q={query}"),"onion search not enabled");
    check(!Router.validTemplate("https://example.org"),"missing query");
    for(String[] e:Router.ENGINES)check(Router.validTemplate(e[1]),e[0]);
    System.out.println("PASS: "+count+" router checks");
  }
}
