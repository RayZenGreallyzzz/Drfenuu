import com.aetherbolt.browser.Router;
import com.aetherbolt.browser.SearchLanguage;
public class SearchLanguageTest {
    static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] a){
        String google=Router.ENGINES[0][1];
        check(SearchLanguage.template(google,"all").equals(google));
        for(String code:new String[]{"uk","ru","en"})check(Router.resolve("hello",SearchLanguage.template(google,code)).url.endsWith("&lr=lang_"+code));
        check(Router.resolve("https://example.com/",SearchLanguage.template(google,"uk")).url.equals("https://example.com/"));
        check(Router.resolve("abc.onion",SearchLanguage.template(google,"uk")).kind==Router.Kind.ONION);
        check(SearchLanguage.template(Router.ENGINES[1][1],"uk").endsWith("kl=ua-uk"));
        check(SearchLanguage.template(Router.ENGINES[3][1],"en").endsWith("+lang%3Aen"));
        check(SearchLanguage.template(Router.ENGINES[2][1],"ru").equals(Router.ENGINES[2][1]));
        System.out.println("PASS: 9 search language checks");
    }
}