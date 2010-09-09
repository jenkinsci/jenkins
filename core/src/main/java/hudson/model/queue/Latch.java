package hudson.model.queue;

/**
 * A concurrency primitive that waits for N number of threads to synchronize.
 * If any of the threads are interrupted while waiting for the completion of the condition,
 * then all the involved threads get interrupted.
 *
 * @author Kohsuke Kawaguchi
 */
class Latch {
    private final int n;
    private int i=0;
    private boolean interrupted;

    public Latch(int n) {
        this.n = n;
    }

    public synchronized void abort() {
        interrupted = true;
        notifyAll();
    }


    public synchronized void synchronize() throws InterruptedException {
        check(n);

        boolean success=false;
        try {
            onCriteriaMet();
            success=true;
        } finally {
            if (!success)
                abort();
        }

        check(n*2);
    }

    private void check(int threshold) throws InterruptedException {
        i++;
        if (i==threshold) {
            notifyAll();
        } else {
            while (i<threshold && !interrupted) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                    notifyAll();
                    throw e;
                }
            }
        }

        // all of us either leave normally or get interrupted
        if (interrupted)
            throw new InterruptedException();
    }

    protected void onCriteriaMet() throws InterruptedException {}
}
