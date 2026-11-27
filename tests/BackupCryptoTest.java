import com.aetherbolt.browser.BackupCrypto;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
public class BackupCryptoTest {
    public static void main(String[] args)throws Exception{
        char[] pw="test-passphrase-123".toCharArray();byte[] p="AetherBolt ⚡".getBytes(StandardCharsets.UTF_8);
        byte[] a=BackupCrypto.encrypt(p,pw),b=BackupCrypto.encrypt(p,pw);
        if(Arrays.equals(a,b)||!Arrays.equals(p,BackupCrypto.decrypt(a,pw)))throw new AssertionError("roundtrip");
        a[a.length-1]^=1;boolean rejected=false;try{BackupCrypto.decrypt(a,pw);}catch(javax.crypto.AEADBadTagException e){rejected=true;}if(!rejected)throw new AssertionError("tamper");
        rejected=false;try{BackupCrypto.decrypt(b,"different-password".toCharArray());}catch(javax.crypto.AEADBadTagException e){rejected=true;}if(!rejected)throw new AssertionError("password");
        System.out.println("BackupCryptoTest passed");
    }
}
