package com.aetherbolt.browser;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;

/** Inspect, never unpack or execute, a bounded local archive. */
public final class ScriptImport {
    public enum Kind { SCRIPTS, EXTENSION, SIGNED_PACKAGE, PACKAGE_ARCHIVE, PROJECT, UNKNOWN }
    public static final class Candidate {
        public final String name,code;
        Candidate(String n,String c){name=n;code=c;}
    }
    public static final class Result {
        public final Kind kind;
        public final List<Candidate> scripts;
        public final String detail,manifestText,manifestPath;
        public final int manifestCount;
        public final List<String> packages;
        Result(Kind k,List<Candidate> s,String d){this(k,s,d,"","",0);}
        Result(Kind k,List<Candidate> s,String d,String manifestText,String manifestPath,int manifestCount){kind=k;scripts=Collections.unmodifiableList(s);detail=d;this.manifestText=manifestText;this.manifestPath=manifestPath;this.manifestCount=manifestCount;this.packages=Collections.emptyList();}
        Result(List<String> packages){kind=Kind.PACKAGE_ARCHIVE;scripts=Collections.emptyList();detail="ZIP с готовыми XPI: выберите пакет; подпись проверит GeckoView.";manifestText="";manifestPath="";manifestCount=0;this.packages=Collections.unmodifiableList(new ArrayList<>(packages));}
    }
    private static Result only(Kind k,String d){return new Result(k,Collections.emptyList(),d);}
    private static String decode(byte[] data)throws IOException{
        try{String s=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
            if(s.indexOf('\0')>=0)throw new IOException("Нужен текст UTF-8");
            return s.startsWith("\ufeff")?s.substring(1):s;
        }catch(CharacterCodingException e){throw new IOException("Нужен текст UTF-8");}
    }
    public static Result read(InputStream input,String name)throws IOException{
        if(input==null)throw new IOException("Нет данных");
        String lower=name.toLowerCase(Locale.ROOT);
        try(BufferedInputStream raw=new BufferedInputStream(input)){
            if(lower.endsWith(".crx"))return only(Kind.EXTENSION,"CRX — пакет Chromium, не XPI Firefox.");
            raw.mark(4);int a=raw.read(),b=raw.read();raw.reset();
            if(a!='P'||b!='K'){
                if(!lower.endsWith(".js")&&!lower.endsWith(".user.js"))return only(Kind.UNKNOWN,"Выберите JS, USER.JS, ZIP или XPI.");
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;
                while((n=raw.read(buf))!=-1){if(out.size()+n>ScriptRules.LIMIT)throw new IOException("JS больше 256 КБ");out.write(buf,0,n);}
                return new Result(Kind.SCRIPTS,Collections.singletonList(new Candidate(name,decode(out.toByteArray()))),"JavaScript");
            }
            boolean manifest=false,rootManifest=false,signature=false,packageJson=false,typescript=false,python=false,modules=false,oversize=false,tooMany=false;
            int entries=0,manifestCount=0;String manifestText="",manifestPath="";long total=0;ArrayList<Candidate> scripts=new ArrayList<>();ArrayList<String> packages=new ArrayList<>();
            try(ZipInputStream zip=new ZipInputStream(raw)){
                ZipEntry e;byte[] buf=new byte[8192];
                while((e=zip.getNextEntry())!=null){
                    if(++entries>512)throw new IOException("ZIP: не более 512 файлов");
                    String path=e.getName().replace('\\','/'),p=path.toLowerCase(Locale.ROOT);
                    if(path.length()>1024)throw new IOException("Слишком длинное имя в ZIP");
                    if(e.isDirectory())continue;
                    boolean ignored=p.startsWith("__macosx/")||p.contains("/__macosx/")||p.startsWith("node_modules/")||p.contains("/node_modules/");
                    String leaf=p.substring(p.lastIndexOf('/')+1);
                    if(!ignored&&leaf.endsWith(".xpi")&&safeEntry(e.getName())){if(packages.size()>=16||packages.contains(e.getName()))throw new IOException("Слишком много XPI или повтор имени");packages.add(e.getName());}
                    if(!ignored){
                        manifest|=leaf.equals("manifest.json");rootManifest|=p.equals("manifest.json");
                        signature|=p.equals("meta-inf/mozilla.rsa");
                        packageJson|=leaf.equals("package.json");
                        typescript|=leaf.endsWith(".ts")||leaf.endsWith(".tsx")||leaf.endsWith(".svelte");
                        python|=leaf.endsWith(".py");modules|=leaf.endsWith(".mjs");
                    }
                    boolean manifestFile=!ignored&&leaf.equals("manifest.json");if(manifestFile)manifestCount++;
                    boolean js=!ignored&&leaf.endsWith(".js")&&!leaf.startsWith("._");
                    ByteArrayOutputStream out=(js||manifestFile)?new ByteArrayOutputStream():null;int n;
                    while((n=zip.read(buf))!=-1){
                        total+=n;if(total>32L*1024*1024)throw new IOException("ZIP: лимит распаковки 32 МБ");
                        if(out!=null){if(out.size()+n>(manifestFile?128*1024:ScriptRules.LIMIT)){out=null;if(js)oversize=true;}else out.write(buf,0,n);}
                    }
                    if(manifestFile){if(out!=null&&(manifestPath.isEmpty()||p.equals("manifest.json"))){manifestPath=path;try{manifestText=decode(out.toByteArray());}catch(IOException invalid){manifestText="";}}}
                    else if(out!=null){if(scripts.size()>=32)tooMany=true;else {try{scripts.add(new Candidate(path,decode(out.toByteArray())));}catch(IOException invalid){/* not a text script */}}}
                }
            }
            // Never treat extension background/content scripts or Node project files as user scripts.
            if(rootManifest&&signature)return new Result(Kind.SIGNED_PACKAGE,Collections.emptyList(),"Пакет Firefox; подпись и совместимость проверит GeckoView.",manifestText,manifestPath,manifestCount);
            if(manifest)return new Result(Kind.EXTENSION,Collections.emptyList(),"WebExtension: нужен совместимый подписанный XPI Firefox. ZIP исходников и CRX Chromium не устанавливаются напрямую.",manifestText,manifestPath,manifestCount);
            if(!packages.isEmpty())return new Result(packages);
            if(packageJson||typescript||python||modules)return only(Kind.PROJECT,"Исходный проект ("+(typescript?"TypeScript / сборка":packageJson?"Node.js / Electron":python?"Python":"ES modules")+"). Нужна соответствующая среда или готовое расширение.");
            if(scripts.isEmpty())return only(Kind.UNKNOWN,oversize?"В ZIP только JS больше 256 КБ":"В архиве нет самостоятельных JS");
            if(tooMany)throw new IOException("В ZIP больше 32 JavaScript-файлов; выберите нужные отдельно");
            return new Result(Kind.SCRIPTS,scripts,oversize?"Часть JS пропущена: больше 256 КБ":"Выберите самостоятельный JavaScript");
        }
    }
    private static boolean safeEntry(String name){
        if(name==null||name.length()>1024||name.startsWith("/")||name.indexOf('\\')>=0||name.indexOf(':')>=0||name.indexOf('\0')>=0)return false;
        for(String part:name.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))return false;
        return true;
    }
    /** Extract only selected XPI bytes to a caller-owned stream, never filesystem paths. */
    public static void copyPackage(InputStream input,String selected,OutputStream output)throws IOException{
        if(input==null||!safeEntry(selected)||!selected.toLowerCase(Locale.ROOT).endsWith(".xpi"))throw new IOException("Недопустимый пакет");
        int count=0,matches=0;long total=0;
        try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(input))){
            ZipEntry e;byte[] buf=new byte[8192];
            while((e=zip.getNextEntry())!=null){
                if(++count>512)throw new IOException("ZIP: не более 512 файлов");
                boolean chosen=!e.isDirectory()&&selected.equals(e.getName());
                if(chosen&&++matches>1)throw new IOException("Повтор имени XPI");
                int n;while((n=zip.read(buf))!=-1){
                    total+=n;if(total>32L*1024*1024)throw new IOException("ZIP: лимит распаковки 32 МБ");
                    if(chosen)output.write(buf,0,n);
                }
            }
        }
        if(matches!=1)throw new IOException("XPI не найден");
        output.flush();
    }
    /** Stream to private staging, rejecting empty files and oversized input before installation. */
    public static long copyBounded(InputStream in,OutputStream out,long limit)throws IOException{
        if(in==null||out==null||limit<1)throw new IOException("Файл недоступен");
        byte[] buf=new byte[8192];long size=0;int n;
        while((n=in.read(buf))!=-1){
            if(n==0)continue;
            if(n>limit-size)throw new IOException("Превышен допустимый размер файла");
            out.write(buf,0,n);size+=n;
        }
        if(size==0)throw new IOException("Пустой файл");
        out.flush();return size;
    }
}
