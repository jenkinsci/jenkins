package hudson.tasks;

import hudson.model.BuildListener;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

/**
 * {@link BuildStep} that uses Ant.
 *
 * Contains helper code.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AntBasedPublisher extends Publisher {
    protected final void execTask(Task task, BuildListener listener) {
        try {
            task.execute();
        } catch( BuildException e ) {
            // failing to archive isn't a fatal error
            e.printStackTrace(listener.error(e.getMessage()));
        }
    }
}
