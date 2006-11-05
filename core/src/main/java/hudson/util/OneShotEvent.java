package hudson.util;

/**
 * Concurrency primitive "event".
 *
 * @author Kohsuke Kawaguchi
 */
public final class OneShotEvent {
    private boolean signaled;

    /**
     * Non-blocking method that signals this event.
     */
    public synchronized void signal() {
        if(signaled)        return;
        this.signaled = true;
        notify();
    }

    /**
     * Blocks until the event becomes the signaled state.
     *
     * <p>
     * This method blocks infinitely until a value is offered.
     */
    public synchronized void block() throws InterruptedException {
        while(!signaled)
            wait();
    }

    /**
     * Blocks until the event becomes the signaled state.
     *
     * <p>
     * If the specified amount of time elapses,
     * this method returns null even if the value isn't offered.
     */
    public synchronized void block(long timeout) throws InterruptedException {
        if(!signaled)
            wait(timeout);
    }

    /**
     * Returns true if a value is offered.
     */
    public synchronized boolean isSignaled() {
        return signaled;
    }
}
