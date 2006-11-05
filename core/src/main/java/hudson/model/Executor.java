package hudson.model;

import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;


/**
 * Thread that executes builds.
 *
 * @author Kohsuke Kawaguchi
 */
public class Executor extends Thread {
    private final Computer owner;
    private final Queue queue;

    private Build build;

    private long startTime;

    public Executor(Computer owner) {
        super("Executor #"+owner.getExecutors().size()+" for "+owner.getDisplayName());
        this.owner = owner;
        this.queue = Hudson.getInstance().getQueue();
        start();
    }

    public void run() {
        while(true) {
            if(Hudson.getInstance().isTerminating())
                return;

            synchronized(owner) {
                if(owner.getNumExecutors()<owner.getExecutors().size()) {
                    // we've got too many executors.
                    owner.removeExecutor(this);
                    return;
                }
            }

            try {
                Project p = queue.pop();
                build = p.newBuild();
            } catch (InterruptedException e) {
                continue;
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }
            startTime = System.currentTimeMillis();
            try {
                build.run();
            } catch (Throwable e) {
                // for some reason the executor died. this is really
                // a bug in the code, but we don't want the executor to die,
                // so just leave some info and go on to build other things
                e.printStackTrace();
            }
            build = null;
        }
    }

    /**
     * Returns the current {@link Build} this executor is running.
     *
     * @return
     *      null if the executor is idle.
     */
    public Build getCurrentBuild() {
        return build;
    }

    /**
     * Returns true if this {@link Executor} is ready for action.
     */
    public boolean isIdle() {
        return build==null;
    }

    /**
     * Returns the progress of the current build in the number between 0-100.
     *
     * @return -1
     *      if it's impossible to estimate the progress.
     */
    public int getProgress() {
        Build b = build.getProject().getLastSuccessfulBuild();
        if(b==null)     return -1;

        long duration = b.getDuration();
        if(duration==0) return -1;

        int num = (int)((System.currentTimeMillis()-startTime)*100/duration);
        if(num>=100)    num=99;
        return num;
    }

    /**
     * Stops the current build.
     */
    public void doStop( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        if(!Hudson.adminCheck(req,rsp))
            return;

        interrupt();
        rsp.forwardToPreviousPage(req);
    }

    public Computer getOwner() {
        return owner;
    }

    /**
     * Returns the executor of the current thread.
     */
    public static Executor currentExecutor() {
        return (Executor)Thread.currentThread();
    }
}
