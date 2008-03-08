package hudson.model;

import hudson.util.ByteBuffer;
import hudson.util.StreamTaskListener;

import java.lang.ref.WeakReference;
import java.io.Reader;
import java.io.IOException;

/**
 * {@link Thread} for performing one-off task.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.191
 */
public abstract class TaskThread extends Thread {
    // StringWriter is synchronized
    protected final ByteBuffer log = new ByteBuffer();
    protected final LargeText text = new LargeText(log,false);

    private final TaskAction owner;

    protected TaskThread(TaskAction owner) {
        super(owner.getBuild().toString()+' '+owner.getDisplayName());
        this.owner = owner;
    }

    public Reader readAll() throws IOException {
        // this method can be invoked from another thread.
        return text.readAll();
    }

    /**
     * Registers that this {@link TaskThread} is run for the specified
     * {@link TaskAction}. This can be explicitly called from subtypes
     * to associate a single {@link TaskThread} across multiple tag actions.
     */
    protected final void associateWith(TaskAction action) {
        action.workerThread = this;
        action.log = new WeakReference<LargeText>(text);
    }

    public synchronized void start() {
        associateWith(owner);
        super.start();
    }

    public final void run() {
        TaskListener listener = new StreamTaskListener(log);
        try {
            perform(listener);
            listener.getLogger().println("Completed");
            owner.workerThread = null;            
        } catch (InterruptedException e) {
            listener.getLogger().println("Aborted");
        } catch (Exception e) {
            e.printStackTrace(listener.getLogger());
        }
        text.markAsComplete();
    }

    /**
     * Do the actual work.
     *
     * @throws Exception
     *      The exception is recorded and reported as a failure.
     */
    protected abstract void perform(TaskListener listener) throws Exception;
}
