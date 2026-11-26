package com.aetherbolt.browser;
import android.content.Context;
import android.content.pm.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;

final class DeltaUpdate {
    private static final long LIMIT=512L*1024*1024;
    private static byte[] hash(File f)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(f)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}return d.digest();}
    private static byte[] take(DataInputStream in,int count)throws IOException{byte[] b=new byte[count];in.readFully(b);return b;}
    static File apply(Context c,InputStream patch)throws Exception{
        if(patch==null)throw new IOException("Не удалось открыть патч");
        if(c.getApplicationInfo().splitSourceDirs!=null&&c.getApplicationInfo().splitSourceDirs.length>0)throw new IOException("Split APK пока не поддерживается");
        File base=new File(c.getApplicationInfo().sourceDir),dir=new File(c.getCacheDir(),"updates");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Нет папки обновлений");
        File target=File.createTempFile("aetherbolt-",".apk",dir);boolean ok=false;
        try{
            byte[] expected;long size;
            try(DataInputStream in=new DataInputStream(new BufferedInputStream(new GZIPInputStream(patch)));RandomAccessFile old=new RandomAccessFile(base,"r");OutputStream out=new BufferedOutputStream(new FileOutputStream(target))){
                if(!Arrays.equals(take(in,4),new byte[]{65,69,66,49})||!MessageDigest.isEqual(take(in,32),hash(base)))throw new IOException("Патч не подходит к установленной версии");
                expected=take(in,32);size=in.readLong();if(size<=0||size>LIMIT||dir.getUsableSpace()<size+16*1024*1024)throw new IOException("Недостаточно места или неверный размер");
                long written=0;byte[] buf=new byte[65536];int ops=0;
                while(true){int tag=in.readUnsignedByte();if(tag==0)break;if(++ops>100000)throw new IOException("Слишком много операций");long pos=tag==2?in.readLong():0;if(tag!=1&&tag!=2)throw new IOException("Неизвестная операция");int len=in.readInt();if(len<=0||len>size-written||pos<0||(tag==2&&(pos>old.length()||len>old.length()-pos)))throw new IOException("Повреждённый патч");
                    if(tag==2)old.seek(pos);for(int left=len;left>0;){int n=Math.min(left,buf.length);if(tag==1)in.readFully(buf,0,n);else old.readFully(buf,0,n);out.write(buf,0,n);left-=n;}written+=len;
                }if(written!=size||in.read()!=-1)throw new IOException("Неполный патч");
            }
            if(!MessageDigest.isEqual(expected,hash(target)))throw new IOException("Контрольная сумма не совпадает");
            PackageManager pm=c.getPackageManager();int flags=android.os.Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;
            PackageInfo current=pm.getPackageInfo(c.getPackageName(),flags), next=pm.getPackageArchiveInfo(target.getAbsolutePath(),flags);
            if(next==null||!current.packageName.equals(next.packageName))throw new IOException("Другой пакет");
            long cv=android.os.Build.VERSION.SDK_INT>=28?current.getLongVersionCode():current.versionCode,nv=android.os.Build.VERSION.SDK_INT>=28?next.getLongVersionCode():next.versionCode;
            if(nv<=cv)throw new IOException("Версия не новее");
            Signature[] a=android.os.Build.VERSION.SDK_INT>=28?(current.signingInfo==null?null:current.signingInfo.getApkContentsSigners()):current.signatures;
            Signature[] b=android.os.Build.VERSION.SDK_INT>=28?(next.signingInfo==null?null:next.signingInfo.getApkContentsSigners()):next.signatures;
            if(a==null||b==null||a.length==0||!new HashSet<>(Arrays.asList(a)).equals(new HashSet<>(Arrays.asList(b))))throw new IOException("Подпись APK не совпадает");
            ok=true;return target;
        }finally{if(!ok)target.delete();}
    }
}
