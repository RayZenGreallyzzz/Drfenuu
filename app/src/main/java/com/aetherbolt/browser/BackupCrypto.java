package com.aetherbolt.browser;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.util.Arrays;

public final class BackupCrypto {
    public static final int MAX=20*1024*1024, HEADER=33, ITERATIONS=600000;
    private static final byte[] MAGIC={'A','E','B','K',1};
    private BackupCrypto(){}
    private static SecretKeySpec key(char[] password,byte[] salt)throws Exception{
        if(password==null||password.length<12||password.length>256)throw new IllegalArgumentException("Password length");
        PBEKeySpec spec=new PBEKeySpec(password,salt,ITERATIONS,256);byte[] raw=null;
        try{raw=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();return new SecretKeySpec(raw,"AES");}
        finally{spec.clearPassword();if(raw!=null)Arrays.fill(raw,(byte)0);}
    }
    public static byte[] encrypt(byte[] plain,char[] password)throws Exception{
        if(plain.length>MAX)throw new IllegalArgumentException("Size");
        byte[] salt=new byte[16],iv=new byte[12];SecureRandom rng=new SecureRandom();rng.nextBytes(salt);rng.nextBytes(iv);
        byte[] header=new byte[HEADER];System.arraycopy(MAGIC,0,header,0,5);System.arraycopy(salt,0,header,5,16);System.arraycopy(iv,0,header,21,12);
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key(password,salt),new GCMParameterSpec(128,iv));c.updateAAD(header);
        byte[] encrypted=c.doFinal(plain),out=Arrays.copyOf(header,HEADER+encrypted.length);System.arraycopy(encrypted,0,out,HEADER,encrypted.length);return out;
    }
    public static byte[] decrypt(byte[] encoded,char[] password)throws Exception{
        if(encoded.length<HEADER+16||encoded.length>MAX+HEADER+16)throw new IllegalArgumentException("Size");
        for(int i=0;i<MAGIC.length;i++)if(encoded[i]!=MAGIC[i])throw new IllegalArgumentException("Format");
        byte[] salt=Arrays.copyOfRange(encoded,5,21),iv=Arrays.copyOfRange(encoded,21,33);
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(password,salt),new GCMParameterSpec(128,iv));c.updateAAD(encoded,0,HEADER);
        return c.doFinal(encoded,HEADER,encoded.length-HEADER);
    }
}
