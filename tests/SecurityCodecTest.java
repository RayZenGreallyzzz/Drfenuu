import com.aetherbolt.browser.SecurityCodec;
public class SecurityCodecTest {
    static void equal(String a,String b){if(!a.equals(b))throw new AssertionError();}
    public static void main(String[] args)throws Exception{
        equal(SecurityCodec.convert(0,"hello"),"aGVsbG8=");
        equal(SecurityCodec.convert(1,"aGVsbG8="),"hello");
        equal(SecurityCodec.convert(2,"a b+c"),"a+b%2Bc");
        equal(SecurityCodec.convert(3,"a+b%2Bc"),"a b+c");
        equal(SecurityCodec.convert(4,"hello"),"68656c6c6f");
        equal(SecurityCodec.convert(5,"68 65 6c 6c 6f"),"hello");
        equal(SecurityCodec.convert(6,"abc"),"ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        for(int n:new int[]{0,2,4})equal(SecurityCodec.convert(n+1,SecurityCodec.convert(n,"Привет ⚡")),"Привет ⚡");
        boolean bad=false;try{SecurityCodec.convert(5,"zz");}catch(IllegalArgumentException e){bad=true;}if(!bad)throw new AssertionError();
        System.out.println("SecurityCodecTest passed");
    }
}
