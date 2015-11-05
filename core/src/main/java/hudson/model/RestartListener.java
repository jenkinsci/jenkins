package hudson.model;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;

import java.io.IOException;
import jenkins.model.queue.AsynchronousExecution;

/**
 * Extension point that allows plugins to veto the restart.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.376
 */
public abstract class RestartListener implements ExtensionPoint {
    /**
     * Called periodically during the safe restart.
     *
     * @return false to block the restart
     */
    public abstract boolean isReadyToRestart() throws IOException, InterruptedException;

    /**
     * Called immediately before the restart is actually triggered.
     */
    public void onRestart() {}

    /**
     * Returns all the registered {@link LabelFinder}s.
     */
    public static ExtensionList<RestartListener> all() {
        return ExtensionList.lookup(RestartListener.class);
    }

    /**
     * Returns true iff all the listeners OKed the restart.
     */
    public static boolean isAllReady() throws IOException, InterruptedException {
        for (RestartListener listener : all()) {
            if (!listener.isReadyToRestart())
                return false;
        }
        return true;
    }

    /**
     * Default logic. Wait for all the executors to become idle.
     * @see AsynchronousExecution#blocksRestart
     */
    @Extension
    public static class Default extends RestartListener {
        @Override
        public boolean isReadyToRestart() throws IOException, InterruptedException {
            for (Computer c : Jenkins.getInstance().getComputers()) {
                if (c.isOnline()) {
                    for (Executor e : c.getExecutors()) {
                        if (blocksRestart(e)) {
                            return false;
                        }
                    }
                    for (Executor e : c.getOneOffExecutors()) {
                        if (blocksRestart(e)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }
        private static boolean blocksRestart(Executor e) {
            if (e.isBusy()) {
                AsynchronousExecution execution = e.getAsynchronousExecution();
                if (execution != null) {
                    return execution.blocksRestart();
                } else {
                    return true;
                }
            } else {
                return false;
            }
        }
    }
}
