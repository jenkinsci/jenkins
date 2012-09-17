package hudson.tasks.test;

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
    
}
