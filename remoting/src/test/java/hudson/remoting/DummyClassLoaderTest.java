package hudson.remoting;

import junit.framework.TestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class DummyClassLoaderTest extends TestCase {
    public void testLoad() throws Throwable {
        DummyClassLoader cl = new DummyClassLoader(this.getClass().getClassLoader());
        Callable c = (Callable) cl.loadClass("hudson.remoting.test.TestCallable").newInstance();
        System.out.println(c.call());
        // make sure that the returned class is loaded from the dummy classloader
        assertTrue(((Object[])c.call())[0].toString().startsWith(DummyClassLoader.class.getName()));
    }
}
