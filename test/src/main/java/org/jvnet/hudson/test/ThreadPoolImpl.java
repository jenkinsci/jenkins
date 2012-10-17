package org.jvnet.hudson.test;

import org.mortbay.component.AbstractLifeCycle;
import org.mortbay.thread.ThreadPool;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Kohsuke Kawaguchi
 */
public class ThreadPoolImpl extends AbstractLifeCycle implements ThreadPool {
    private final ExecutorService es;

    public ThreadPoolImpl(ExecutorService es) {
        this.es = es;
    }

    public boolean dispatch(Runnable job) {
        if (!isRunning() || job==null)
            return false;

        es.submit(job);
        return true;
    }

    public void join() throws InterruptedException {
        while(!es.awaitTermination(999 * 60 * 60 * 24, TimeUnit.SECONDS))
            ;
    }

    public int getThreads() {
        return 999;
    }

    public int getIdleThreads() {
        return 999;
    }

    public boolean isLowOnThreads() {
        return false;
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        es.shutdown();
    }
}
