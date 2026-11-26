import com.aetherbolt.browser.ScriptRules;
public class ScriptRulesTest{
 static int n;
 static void t(boolean b){n++;if(!b)throw new AssertionError("case "+n);}
 public static void main(String[] x){
 t(ScriptRules.origin("https://Example.com:443/a").equals("https://example.com"));
 t(ScriptRules.origin("http://example.com:80").equals("http://example.com"));
 t(ScriptRules.origin("https://example.com:8443/a").equals("https://example.com:8443"));
 t(ScriptRules.origin("https://example.com.evil.org").equals("https://example.com.evil.org"));
 t(ScriptRules.origin("http://example.onion").isEmpty());
 t(ScriptRules.origin("file:///a").isEmpty());
 t(ScriptRules.origin("https://example.com@evil.org").isEmpty());
 t(ScriptRules.scope("https://example.com/").equals("https://example.com"));
 t(ScriptRules.scope("https://example.com/a").isEmpty());
 t(ScriptRules.scope("https://*.example.com").isEmpty());
 t(ScriptRules.validate("document.title='hello';").isEmpty());
 t(ScriptRules.validate("// @grant none\nalert(1);").isEmpty());
 t(!ScriptRules.validate("// @grant GM_xmlhttpRequest\n").isEmpty());
 t(!ScriptRules.validate("// @require https://example.org/x.js\n").isEmpty());
 t(!ScriptRules.validate("// @run-at document-start\n").isEmpty());
 t(!ScriptRules.validate("// @match https://example.org/*\n").isEmpty());
 t(ScriptRules.metadata("// @name Demo\n","name").equals("Demo"));
 System.out.println("PASS: "+n+" script rules checks");
 }
}
