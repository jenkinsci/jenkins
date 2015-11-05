package hudson.util

import org.junit.After
import org.junit.Test

import java.nio.charset.Charset

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class TextFileTest {
    List<File> files = [];

    @After
    void tearDown() {
        files*.delete()
    }

    @Test
    public void head() {
        def f = newFile()
        f.text = getClass().getResource("ascii.txt").text

        def t = new TextFile(f)
        def first35 = "Lorem ipsum dolor sit amet, consect"
        assert t.head(35).equals(first35)
        assert first35.length()==35
    }

    @Test
    public void shortHead() {
        def f = newFile()
        f.text = "hello"

        def t = new TextFile(f)
        assert t.head(35).equals("hello")
    }

    @Test
    public void tail() {
        def f = newFile()
        f.text = getClass().getResource("ascii.txt").text

        def t = new TextFile(f)
        def tail35 = "la, vitae interdum quam rutrum id.\n"
        assert t.fastTail(35).equals(tail35)
        assert tail35.length()==35
    }

    @Test
    public void shortTail() {
        def f = newFile()
        f.text = "hello"

        def t = new TextFile(f)
        assert t.fastTail(35).equals("hello")
    }

    /**
     * Shift JIS is a multi-byte character encoding.
     *
     * In it, 0x82 0x83 is \u30e2, and 0x83 0x82 is \uFF43.
     * So if aren't careful, we'll parse the text incorrectly.
     */
    @Test
    public void tailShiftJIS() {
        def f = newFile()

        def t = new TextFile(f)

        f.withOutputStream { o ->
            (1..80).each {
                (1..40).each {
                    o.write(0x83)
                    o.write(0x82)
                }
                o.write(0x0A);
            }
        }

        def tail = t.fastTail(35, Charset.forName("Shift_JIS"))
        assert tail.equals("\u30e2"*34+"\n")
        assert tail.length()==35

        // add one more byte to force fastTail to read from one byte ahead
        // between this and the previous case, it should start parsing text incorrectly, until it hits NL
        // where it comes back in sync
        f.append([0x0A] as byte[])

        tail = t.fastTail(35, Charset.forName("Shift_JIS"))
        assert tail.equals("\u30e2"*33+"\n\n")
        assert tail.length()==35
    }

    def newFile() {
        def f = File.createTempFile("foo", "txt")
        files.add(f)
        return f
    }
}
