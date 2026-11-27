import com.aetherbolt.browser.ScriptImport;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
public class ScriptImportTest {
 static void t(boolean ok){if(!ok)throw new AssertionError();}
 static ScriptImport.Result zip(String... paths)throws Exception{
  ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(out)){for(String p:paths){z.putNextEntry(new ZipEntry(p));z.write("alert(1)".getBytes(StandardCharsets.UTF_8));z.closeEntry();}}
  return ScriptImport.read(new ByteArrayInputStream(out.toByteArray()),"test.zip");
 }
 public static void main(String[] args)throws Exception{
  t(zip("folder/a.js").kind==ScriptImport.Kind.SCRIPTS);
  t(zip("a.js","b.user.js").scripts.size()==2);
  t(zip("a.js","folder/manifest.json").kind==ScriptImport.Kind.EXTENSION);
  t(zip("a.js","folder/manifest.json").manifestCount==1);
  t(zip("a.js","folder/manifest.json").manifestPath.equals("folder/manifest.json"));
  t(zip("manifest.json","folder/manifest.json").manifestCount==2);
  t(zip("manifest.json","folder/manifest.json").manifestPath.equals("manifest.json"));
  t(zip("manifest.json","META-INF/mozilla.rsa","a.js").kind==ScriptImport.Kind.SIGNED_PACKAGE);
  t(zip("src/a.ts","package.json").kind==ScriptImport.Kind.PROJECT);
  t(zip("main.js","package.json").kind==ScriptImport.Kind.PROJECT);
  t(zip("src/a.py").kind==ScriptImport.Kind.PROJECT);
  t(zip("README.md").kind==ScriptImport.Kind.UNKNOWN);
  t(zip("__MACOSX/a.js","a.js").scripts.size()==1);
  t(ScriptImport.read(new ByteArrayInputStream("alert(1)".getBytes(StandardCharsets.UTF_8)),"test.user.js").kind==ScriptImport.Kind.SCRIPTS);
  System.out.println("PASS: script import classification");
 }
}
