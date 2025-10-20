package hudson.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextFileTest {

    @TempDir
    private File tmp;

    @Test
    void head() throws Exception {
        File f = File.createTempFile("junit", null, tmp);
        FileUtils.copyURLToFile(getClass().getResource("ascii.txt"), f);

        TextFile t = new TextFile(f);
        String first35 = "Lorem ipsum dolor sit amet, consect";
        assertEquals(35, first35.length());
        assertEquals(first35, t.head(35));
    }

    @Test
    void shortHead() throws Exception {
        File f = File.createTempFile("junit", null, tmp);
        Files.writeString(f.toPath(), "hello", Charset.defaultCharset());

        TextFile t = new TextFile(f);
        assertEquals("hello", t.head(35));
    }

    @Test
    void tail() throws Exception {
        File f = File.createTempFile("junit", null, tmp);
        FileUtils.copyURLToFile(getClass().getResource("ascii.txt"), f);
        String whole = Files.readString(f.toPath(), Charset.defaultCharset());
        TextFile t = new TextFile(f);
        String tailStr = whole.substring(whole.length() - 34);
        assertEquals(tailStr, t.fastTail(tailStr.length()));
    }

    @Test
    void shortTail() throws Exception {
        File f = File.createTempFile("junit", null, tmp);
        Files.writeString(f.toPath(), "hello", Charset.defaultCharset());

        TextFile t = new TextFile(f);
        assertEquals("hello", t.fastTail(35));
    }

    /**
     * Shift JIS is a multi-byte character encoding.
     *
     * In it, 0x82 0x83 is \u30e2, and 0x83 0x82 is \uFF43. So if aren't
     * careful, we'll parse the text incorrectly.
     */
    @Test
    void tailShiftJIS() throws Exception {
        File f = File.createTempFile("junit", null, tmp);

        TextFile t = new TextFile(f);

        try (OutputStream o = new FileOutputStream(f)) {
            for (int i = 0; i < 80; i++) {
                for (int j = 0; j < 40; j++) {
                    o.write(0x83);
                    o.write(0x82);
                }
                o.write(0x0A);
            }
        }

        String tail = t.fastTail(35, Charset.forName("Shift_JIS"));
        assertEquals("\u30e2".repeat(34) + "\n", tail);
        assertEquals(35, tail.length());

        // add one more byte to force fastTail to read from one byte ahead
        // between this and the previous case, it should start parsing text incorrectly, until it hits NL
        // where it comes back in sync
        try (OutputStream o = new FileOutputStream(f, true)) {
            o.write(0x0A);
        }

        tail = t.fastTail(35, Charset.forName("Shift_JIS"));
        assertEquals("\u30e2".repeat(33) + "\n\n", tail);
        assertEquals(35, tail.length());
    }

}
