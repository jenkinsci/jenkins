package hudson.tasks.test;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class TestObjectTest {

    @Test
    public void testSafe() {
        String name = "Foo#approve! is called by approve_on_foo?xyz/\\:";
        String encoded = TestObject.safe(name);
        
        Assert.assertFalse(encoded.contains("#"));
        Assert.assertFalse(encoded.contains("?"));
        Assert.assertFalse(encoded.contains("\\"));
        Assert.assertFalse(encoded.contains("/"));
        Assert.assertFalse(encoded.contains(":"));
    }

    @Test public void uniquifyName() {
        for (int i = 0; i < 2; i++) { // different parents
            final List<TestObject> ts = new ArrayList<TestObject>();
            for (int j = 0; j < 10; j++) {
                final String name = "t" + (int) Math.sqrt(j); // partly unique names
                ts.add(new SimpleCaseResult() {
                    @Override public String getSafeName() {
                        return uniquifyName(ts, name);
                    }
                });
            }
            List<String> names = new ArrayList<String>();
            for (TestObject t : ts) {
                names.add(t.getSafeName());
            }
            Assert.assertEquals("[t0, t1, t1_2, t1_3, t2, t2_2, t2_3, t2_4, t2_5, t3]", names.toString());
            Reference<?> r = new WeakReference<Object>(ts.get(4)); // arbitrarily
            ts.clear();
            System.gc();
            Assert.assertNull(r.get());
        }
    }
    
}
