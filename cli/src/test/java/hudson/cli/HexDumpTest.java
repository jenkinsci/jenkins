package hudson.cli;

import org.junit.Assert;
import org.junit.Test;

public class HexDumpTest {

  @Test
  public void testToHex1() {
    Assert.assertEquals("'fooBar'",
            HexDump.toHex("fooBar".getBytes()));
    Assert.assertEquals("0xc3 0x83",
            HexDump.toHex("Ã".getBytes()));
    Assert.assertEquals("0xe2 0x82 0xac '100'",
            HexDump.toHex("€100".getBytes()));
    Assert.assertEquals("'1' 0xc3 0xb7 '2'",
            HexDump.toHex("1÷2".getBytes()));
    Assert.assertEquals("'foo' 0x0a\n'Bar'",
            HexDump.toHex("foo\nBar".getBytes()));
  }

  @Test
  public void testToHex2() {
    Assert.assertEquals("'ooBa'",
            HexDump.toHex("fooBar".getBytes(), 1, 4));
    Assert.assertEquals("0xc3 0x83",
            HexDump.toHex("Ã".getBytes(), 0, 2));
    Assert.assertEquals("0xe2 0x82 0xac '10'",
            HexDump.toHex("€100".getBytes(), 0, 5));
    Assert.assertEquals("0xc3 0xb7",
            HexDump.toHex("1÷2".getBytes(), 1, 2));
    Assert.assertEquals("'Bar'",
            HexDump.toHex("foo\nBar".getBytes(), 4, 3));
    Assert.assertEquals("",
            HexDump.toHex("fooBar".getBytes(), 0, 0));
  }
}
