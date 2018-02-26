package hudson.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 *  Explicitly sets the {@link Thread#contextClassLoader} for threads it creates to its own classloader.
 *  This avoids issues where threads are lazily created (ex by invoking {@link java.util.concurrent.ScheduledExecutorService#schedule(Runnable, long, TimeUnit)})
 *   in a context where they would receive a customized {@link Thread#contextClassLoader} that was never meant to be used.
 *
 *  Commonly this is a problem for Groovy use, where this may result in memory leaks.
 *  @see <a href="https://issues.jenkins-ci.org/browse/JENKINS-49206">JENKINS-49206</a>
 * @since 2.105
 */
public class ClassLoaderSanityThreadFactory implements ThreadFactory {
    private final ThreadFactory delegate;

    public ClassLoaderSanityThreadFactory(ThreadFactory delegate) {
        this.delegate = delegate;
    }

    @Override public Thread newThread(Runnable r) {
        Thread t = delegate.newThread(r);
        t.setContextClassLoader(ClassLoaderSanityThreadFactory.class.getClassLoader());
        return t;
    }
}
