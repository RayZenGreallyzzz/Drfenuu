package com.aetherbolt.browser;
import java.security.SecureRandom;
public final class VaultPasswords {
    private VaultPasswords(){}
    private static final SecureRandom RNG=new SecureRandom();
    private static final String[] GROUPS={"ABCDEFGHIJKLMNOPQRSTUVWXYZ","abcdefghijklmnopqrstuvwxyz","0123456789","!@#$%^&*_-+="};
    public static String generate(int size){
        if(size<16||size>64)throw new IllegalArgumentException("Length");
        String all=String.join("",GROUPS);char[] chars=new char[size];
        for(int i=0;i<GROUPS.length;i++)chars[i]=GROUPS[i].charAt(RNG.nextInt(GROUPS[i].length()));
        for(int i=GROUPS.length;i<size;i++)chars[i]=all.charAt(RNG.nextInt(all.length()));
        for(int i=size-1;i>0;i--){int j=RNG.nextInt(i+1);char t=chars[i];chars[i]=chars[j];chars[j]=t;}
        return new String(chars);
    }
}
