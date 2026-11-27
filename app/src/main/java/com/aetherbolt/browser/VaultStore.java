package com.aetherbolt.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONArray;

/** Single authenticated, versioned encrypted envelope. No plaintext persistence. */
final class VaultStore {
    private static final String ALIAS="aetherbolt.vault.aes.v1", DATA="envelope";
    private static final byte[] AAD="AetherBolt.PasswordVault.v1".getBytes(StandardCharsets.UTF_8);
    private static final int MAX=1024*1024;
    private final SharedPreferences prefs;
    VaultStore(Context c){prefs=c.getSharedPreferences("aetherbolt_password_vault",Context.MODE_PRIVATE);}
    private KeyStore keyStore()throws Exception{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);return ks;}
    synchronized void prepare()throws Exception{
        KeyStore ks=keyStore();
        if(ks.containsAlias(ALIAS))return;
        if(prefs.contains(DATA))throw new java.security.KeyStoreException("Existing vault key unavailable");
        KeyGenParameterSpec.Builder b=new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(true);
        if(Build.VERSION.SDK_INT>=30)b.setUserAuthenticationParameters(60,KeyProperties.AUTH_BIOMETRIC_STRONG|KeyProperties.AUTH_DEVICE_CREDENTIAL);
        else b.setUserAuthenticationValidityDurationSeconds(60);
        KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        kg.init(b.build());kg.generateKey();
    }
    private SecretKey key()throws Exception{
        java.security.Key k=keyStore().getKey(ALIAS,null);
        if(!(k instanceof SecretKey))throw new java.security.KeyStoreException("Vault key unavailable");return (SecretKey)k;
    }
    synchronized JSONArray read()throws Exception{
        String saved=prefs.getString(DATA,null);
        if(saved==null){
            JSONArray empty=new JSONArray();write(empty);return empty;
        }
        if(saved.length()>2*MAX)throw new java.io.IOException("Envelope too large");
        byte[] envelope=Base64.decode(saved,Base64.NO_WRAP),plain=null;
        try{
            if(envelope.length<29||envelope[0]!=1)throw new java.io.IOException("Invalid envelope");
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,envelope,1,12));c.updateAAD(AAD);
            plain=c.doFinal(envelope,13,envelope.length-13);
            if(plain.length>MAX)throw new java.io.IOException("Vault too large");
            return new JSONArray(new String(plain,StandardCharsets.UTF_8));
        }finally{Arrays.fill(envelope,(byte)0);if(plain!=null)Arrays.fill(plain,(byte)0);}
    }
    synchronized void write(JSONArray accounts)throws Exception{
        byte[] plain=accounts.toString().getBytes(StandardCharsets.UTF_8),encrypted=null,envelope=null;
        try{
            if(plain.length>MAX)throw new java.io.IOException("Vault too large");
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE,key());c.updateAAD(AAD);
            encrypted=c.doFinal(plain);byte[] iv=c.getIV();if(iv==null||iv.length!=12)throw new java.security.GeneralSecurityException("Invalid IV");
            envelope=new byte[13+encrypted.length];envelope[0]=1;System.arraycopy(iv,0,envelope,1,12);System.arraycopy(encrypted,0,envelope,13,encrypted.length);
            if(!prefs.edit().putString(DATA,Base64.encodeToString(envelope,Base64.NO_WRAP)).commit())throw new java.io.IOException("Save failed");
        }finally{Arrays.fill(plain,(byte)0);if(encrypted!=null)Arrays.fill(encrypted,(byte)0);if(envelope!=null)Arrays.fill(envelope,(byte)0);}
    }
}
