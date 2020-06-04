package hudson.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Execution(ExecutionMode.CONCURRENT)
public class HexDumpTest {

  @Test
  public void testToHex1() {
    assertEquals("'fooBar'",
            HexDump.toHex(new byte[] {'f', 'o', 'o', 'B', 'a', 'r'}));
    assertEquals("0xc3",
            HexDump.toHex(new byte[] {(byte)'Ã'}));
    assertEquals("0xac '100'",
            HexDump.toHex(new byte[] {(byte)'€', '1', '0', '0'}));
    assertEquals("'1' 0xf7 '2'",
            HexDump.toHex(new byte[] {'1', (byte)'÷', '2'}));
    assertEquals("'foo' 0x0a\n'Bar'",
            HexDump.toHex(new byte[] {'f', 'o', 'o', '\n', 'B', 'a', 'r'}));
  }

  @Test
  public void testToHex2() {
    assertEquals("'ooBa'",
            HexDump.toHex(new byte[] {'f', 'o', 'o', 'B', 'a', 'r'}, 1, 4));
    assertEquals("0xc3",
            HexDump.toHex(new byte[] {(byte)'Ã'}, 0, 1));
    assertEquals("0xac '10'",
            HexDump.toHex(new byte[] {(byte)'€', '1', '0', '0'}, 0, 3));
    assertEquals("0xf7 '2'",
            HexDump.toHex(new byte[] {'1', (byte)'÷', '2'}, 1, 2));
    assertEquals("'Bar'",
            HexDump.toHex(new byte[] {'f', 'o', 'o', '\n', 'B', 'a', 'r'}, 4, 3));
    assertEquals("",
            HexDump.toHex(new byte[] {'f', 'o', 'o', 'B', 'a', 'r'}, 0, 0));
  }
}
