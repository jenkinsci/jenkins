package hudson.remoting;

import junit.framework.Test;
import junit.framework.TestSuite;

import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;

/**
 * @author Kohsuke Kawaguchi
 */
public class SimpleTest extends RmiTestBase {
    public void test1() throws Exception {
        int r = channel.call(new Callable1());
        System.out.println("result=" + r);
        assertEquals(5,r);
    }

    public void test1Async() throws Exception {
        Future<Integer> r = channel.callAsync(new Callable1());
        System.out.println("result="+r.get());
        assertEquals(5,(int)r.get());
    }

    private static class Callable1 implements Callable<Integer, RuntimeException> {
        public Integer call() throws RuntimeException {
            System.out.println("invoked");
            return 5;
        }
    }


    public void test2() throws Exception {
        try {
            channel.call(new Callable2());
            fail();
        } catch (RuntimeException e) {
            assertEquals(e.getMessage(),"foo");
        }
    }

    public void test2Async() throws Exception {
        try {
            Future<Integer> r = channel.callAsync(new Callable2());
            r.get();
            fail();
        } catch (ExecutionException e) {
            assertEquals(e.getCause().getMessage(),"foo");
        }
    }

    private static class Callable2 implements Callable<Integer, RuntimeException> {
        public Integer call() throws RuntimeException {
            throw new RuntimeException("foo");
        }
    }


    public static Test suite() throws Exception {
        TestSuite suite = new TestSuite();
        for( Class<? extends ChannelRunner> r : ChannelRunner.LIST ) {
            suite.addTest(new ChannelTestSuite(SimpleTest.class,r));
        }
        return suite;
    }
}
