package hudson.cli;

// TODO COPIED FROM hudson.remoting

/**
 * @author Kohsuke Kawaguchi
 */
class HexDump {
    private static final String CODE = "0123456789abcdef";

    public static String toHex(byte[] buf) {
        return toHex(buf,0,buf.length);
    }
    public static String toHex(byte[] buf, int start, int len) {
        StringBuilder r = new StringBuilder(len*2);
        boolean inText = false;
        for (int i=0; i<len; i++) {
            byte b = buf[start+i];
            if (b >= 0x20 && b <= 0x7e) {
                if (!inText) {
                    inText = true;
                    r.append('\'');
                }
                r.append((char) b);
            } else {
                if (inText) {
                    r.append("' ");
                    inText = false;
                }
                r.append("0x");
                r.append(CODE.charAt((b>>4)&15));
                r.append(CODE.charAt(b&15));
                if (i < len - 1) {
                    if (b == 10) {
                        r.append('\n');
                    } else {
                        r.append(' ');
                    }
                }
            }
        }
        if (inText) {
            r.append('\'');
        }
        return r.toString();
    }
}
