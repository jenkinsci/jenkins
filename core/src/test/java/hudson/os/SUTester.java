package hudson.os;

import hudson.util.StreamTaskListener;
import java.io.File;
import java.nio.file.Files;
import jenkins.security.MasterToAgentCallable;

/**
 * @author Kohsuke Kawaguchi
 */
public class SUTester {
    public static void main(String[] args) throws Throwable {
        SU.execute(StreamTaskListener.fromStdout(), "kohsuke", "bogus", new TouchingCallable());
    }

    private static class TouchingCallable extends MasterToAgentCallable<Object, Throwable> {
        @Override
        public Object call() throws Throwable {
            System.out.println("Touching /tmp/x");
            Files.newOutputStream(new File("/tmp/x").toPath()).close();
            return null;
        }
    }
}
