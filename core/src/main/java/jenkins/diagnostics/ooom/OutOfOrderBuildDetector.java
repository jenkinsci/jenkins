package jenkins.diagnostics.ooom;

import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Job;
import hudson.model.TaskListener;
import hudson.util.TimeUnit2;
import jenkins.model.Jenkins;

import javax.inject.Inject;
import java.io.IOException;

/**
 * Discovers {@link Problem}s periodically in the background and
 * pass them on to {@link OutOfOrderBuildMonitor}.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class OutOfOrderBuildDetector extends AsyncPeriodicWork {
    @Inject
    private OutOfOrderBuildMonitor monitor;


    public OutOfOrderBuildDetector() {
        super("Out of order build detection");
    }

    @Override
    protected void execute(TaskListener listener) throws IOException, InterruptedException {
        execute(listener, 10*1000);
    }

    /**
     * Performs the check synchronously.
     *
     * @param delay
     *      delay in the number of milli-seconds to reduce the load on I/O.
     */
    public void execute(TaskListener listener, int delay) throws InterruptedException {
        for (Job j : Jenkins.getInstance().getAllItems(Job.class)) {
            listener.getLogger().println("Scanning "+j.getFullDisplayName());

            Problem p = Problem.find(j);
            if (p!=null) {
                monitor.addProblem(p);
                listener.getLogger().println("  found problems: "+p);
            }

            Thread.sleep(delay);
        }
    }

    @Override
    public long getRecurrencePeriod() {
        return TimeUnit2.DAYS.toMillis(1);
    }
}
