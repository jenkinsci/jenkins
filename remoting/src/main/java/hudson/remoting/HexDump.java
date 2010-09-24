package hudson.remoting;

/**
 * @author Kohsuke Kawaguchi
 */
public class HexDump {
    private static final String CODE = "0123456789abcdef";

    public static String toHex(byte[] buf) {
        return toHex(buf,0,buf.length);
    }
    public static String toHex(byte[] buf, int start, int len) {
        StringBuilder r = new StringBuilder(len*2);
        for (int i=0; i<len; i++) {
            byte b = buf[start+i];
            r.append(CODE.charAt((b>>4)&15));
            r.append(CODE.charAt(b&15));
        }
        return r.toString();
    }
}
