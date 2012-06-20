package jenkins.util;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Test;

/**
 * Tests the class {@link TreeStringBuilder}.
 *
 * @author Kohsuke Kawaguchi
 */
@SuppressWarnings({"PMD", "all"})
//CHECKSTYLE:OFF
public class TreeStringBuilderTest {
    /**
     * Tests the simple operations inside the builder.
     */
    @Test
    public void test() {
        TreeStringBuilder b = new TreeStringBuilder();
        verify("foo", b.intern("foo"));
        TreeString s = b.intern("foo/bar/zot");
        verify("foo/bar/zot", s);
        verify("", b.intern(""));
        verify("foo/bar/xxx", b.intern("foo/bar/xxx")); // this will create new
                                                        // middle node
        verify("foo/bar/zot", s); // make sure existing strings aren't affected
    }

    /**
     * Pseudo random (but deterministic) test.
     */
    @Test
    public void testRandom() {
        String[] dict = new String[]{"aa","b","aba","ba"};
        TreeStringBuilder x = new TreeStringBuilder();
        Random r = new Random(0);

        List<String> a = new ArrayList<String>();
        List<TreeString> o = new ArrayList<TreeString>();

        for (int i = 0; i < 1000; i++) {
            StringBuilder b = new StringBuilder();
            for (int j = 0; j < r.nextInt(10) + 3; j++) {
                b.append(dict[r.nextInt(4)]);
            }
            String s = b.toString();

            a.add(s);
            TreeString p = x.intern(s);
            verify(s, p);
            o.add(p);
        }

        // make sure values are still all intact
        for (int i = 0; i < a.size(); i++) {
            verify(a.get(i), o.get(i));
        }

        x.dedup();

        // verify one more time
        for (int i = 0; i < a.size(); i++) {
            verify(a.get(i), o.get(i));
        }
    }

    private void verify(final String s, final TreeString t) {
        assertEquals(s, t.toString());
    }
}
