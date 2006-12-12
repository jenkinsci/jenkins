package hudson.remoting;

/**
 * @author Kohsuke Kawaguchi
 */
public class ClassRemotingTest extends RmiTestBase {
    public void test1() throws Throwable {
        DummyClassLoader cl = new DummyClassLoader(this.getClass().getClassLoader());
        Callable c = (Callable) cl.loadClass("hudson.remoting.test.TestCallable").newInstance();

        Object r = channel.call(c);

        System.out.println(r);

        assertTrue(r.toString().startsWith("hudson.remoting.RemoteClassLoader@"));
    }
}
