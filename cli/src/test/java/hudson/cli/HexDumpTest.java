package hudson.cli;

import org.junit.Assert;
import org.junit.Test;

public class HexDumpTest {

  @Test
  public void testToHex1() {
    Assert.assertEquals("'fooBar'",
            HexDump.toHex(new byte[] {'f', 'o', 'o', 'B', 'a', 'r'}));
    Assert.assertEquals("0xc3",
            HexDump.toHex(new byte[] {(byte)'Ã'}));
    Assert.assertEquals("0xac '100'",
            HexDump.toHex(new byte[] {(byte)'€', '1', '0', '0'}));
    Assert.assertEquals("'1' 0xf7 '2'",
            HexDump.toHex(new byte[] {'1', (byte)'÷', '2'}));
    Assert.assertEquals("'foo' 0x0a\n'Bar'",
            HexDump.toHex(new byte[] {'f', 'o', 'o', '\n', 'B', 'a', 'r'}));
  }

  @Test
  public void testToHex2() {
    Assert.assertEquals("'ooBa'",
            HexDump.toHex(new byte[] {'f', 'o', 'o', 'B', 'a', 'r'}, 1, 4));
    Assert.assertEquals("0xc3",
            HexDump.toHex(new byte[] {(byte)'Ã'}, 0, 1));
    Assert.assertEquals("0xac '10'",
            HexDump.toHex(new byte[] {(byte)'€', '1', '0', '0'}, 0, 3));
    Assert.assertEquals("0xf7 '2'",
            HexDump.toHex(new byte[] {'1', (byte)'÷', '2'}, 1, 2));
    Assert.assertEquals("'Bar'",
            HexDump.toHex(new byte[] {'f', 'o', 'o', '\n', 'B', 'a', 'r'}, 4, 3));
    Assert.assertEquals("",
            HexDump.toHex(new byte[] {'f', 'o', 'o', 'B', 'a', 'r'}, 0, 0));
  }
}
