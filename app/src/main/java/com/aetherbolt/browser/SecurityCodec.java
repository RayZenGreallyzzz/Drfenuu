package com.aetherbolt.browser;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.Base64;

public final class SecurityCodec {
    private SecurityCodec(){}
    public static String convert(int mode,String value)throws Exception{
        if(value==null||value.length()>65536)throw new IllegalArgumentException("Input");
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
        switch(mode){
            case 0:return Base64.getEncoder().encodeToString(bytes);
            case 1:return new String(Base64.getDecoder().decode(value.trim()),StandardCharsets.UTF_8);
            case 2:return URLEncoder.encode(value,"UTF-8");
            case 3:return URLDecoder.decode(value,"UTF-8");
            case 4:return hex(bytes);
            case 5:
                String clean=value.replaceAll("\\s+","");
                if((clean.length()&1)!=0||!clean.matches("[0-9a-fA-F]*"))throw new IllegalArgumentException("Hex");
                byte[] decoded=new byte[clean.length()/2];
                for(int i=0;i<decoded.length;i++)decoded[i]=(byte)Integer.parseInt(clean.substring(i*2,i*2+2),16);
                return new String(decoded,StandardCharsets.UTF_8);
            case 6:return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
            default:throw new IllegalArgumentException("Mode");
        }
    }
    private static String hex(byte[] bytes){StringBuilder b=new StringBuilder(bytes.length*2);char[] alphabet="0123456789abcdef".toCharArray();for(byte x:bytes){b.append(alphabet[(x&255)>>>4]);b.append(alphabet[x&15]);}return b.toString();}
}
