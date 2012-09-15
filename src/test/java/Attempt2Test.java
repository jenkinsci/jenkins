import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class Attempt2Test extends Assert {
    @Rule
    public FakeMapBuilder builder = new FakeMapBuilder();

    @Test
    public void testSomething() throws IOException {
        FakeMap map = builder.add(1, "A").add(3, "B").add(5, "C").make();

        map.get(1).asserts(1,"A");
        assertNull(map.get(2));
        assertNull(map.get(4));
        map.get(3).asserts(3,"B");
        map.get(5).asserts(5,"C");
    }

}
