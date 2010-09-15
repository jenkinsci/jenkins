package hudson.remoting;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * {@link ExecutorService} that executes synchronously.
 *
 * @author Kohsuke Kawaguchi
 */
class SynchronousExecutorService extends AbstractExecutorService {
    private volatile boolean shutdown = false;
    private int count = 0;

    public void shutdown() {
        shutdown = true;
    }

    public List<Runnable> shutdownNow() {
        shutdown = true;
        return Collections.emptyList();
    }

    public boolean isShutdown() {
        return shutdown;
    }

    public synchronized boolean isTerminated() {
        return shutdown && count==0;
    }

    public synchronized boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long end = System.currentTimeMillis() + unit.toMillis(timeout);

        while (count!=0) {
            long d = end - System.currentTimeMillis();
            if (d<0)    return false;
            wait(d);
        }
        return true;
    }

    public void execute(Runnable command) {
        if (shutdown)
            throw new IllegalStateException("Already shut down");
        touchCount(1);
        try {
            command.run();
        } finally {
            touchCount(-1);
        }
    }

    private synchronized void touchCount(int diff) {
        count += diff;
        if (count==0)
            notifyAll();
    }
}
