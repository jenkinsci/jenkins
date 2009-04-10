package hudson.os;

import hudson.remoting.Callable;
import hudson.util.StreamTaskListener;

import java.io.FileOutputStream;

/**
 * @author Kohsuke Kawaguchi
 */
public class SUTester {
    public static void main(String[] args) throws Throwable {
        SU.execute(new StreamTaskListener(System.out),null,null,new Callable<Object, Throwable>() {
            public Object call() throws Throwable {
                new FileOutputStream("/tmp/x").close();
                return null;
            }
        });
    }
}
