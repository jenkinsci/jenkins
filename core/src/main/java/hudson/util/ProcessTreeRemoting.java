package hudson.util;

import hudson.EnvVars;
import hudson.util.ProcessTree.ProcessCallable;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

/**
 * Remoting interfaces of {@link ProcessTree}.
 *
 * These classes need to be public due to the way {@link Proxy} works.
 *
 * @author Kohsuke Kawaguchi
 */
public class ProcessTreeRemoting {
    public interface IProcessTree {
        void killAll(@NonNull Map<String, String> modelEnvVars) throws InterruptedException;
    }

    public interface IOSProcess {
        int getPid();
        @CheckForNull
        IOSProcess getParent();
        void kill() throws InterruptedException;
        void killRecursively() throws InterruptedException;
        @NonNull
        List<String> getArguments();
        @NonNull
        EnvVars getEnvironmentVariables();
        <T> T act(ProcessCallable<T> callable) throws IOException, InterruptedException;
    }
}
