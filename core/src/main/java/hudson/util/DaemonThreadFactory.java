package hudson.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * {@link ThreadFactory} that creates daemon threads.
 *
 * @author Kohsuke Kawaguchi
 */
public class DaemonThreadFactory implements ThreadFactory {
    private final ThreadFactory core;

    public DaemonThreadFactory() {
        this(Executors.defaultThreadFactory());
    }

    public DaemonThreadFactory(ThreadFactory core) {
        this.core = core;
    }

    public Thread newThread(Runnable r) {
        Thread t = core.newThread(r);
        t.setDaemon(true);
        return t;
    }
}
