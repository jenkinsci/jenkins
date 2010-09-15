package hudson.remoting;

import junit.framework.TestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class HexDumpTest extends TestCase {
    public  void test1() {
        assertEquals("0001ff",HexDump.toHex(new byte[]{0,1,-1}));
    }
}
