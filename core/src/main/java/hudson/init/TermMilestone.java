package hudson.init;

import org.jvnet.hudson.reactor.Executable;
import org.jvnet.hudson.reactor.Milestone;
import org.jvnet.hudson.reactor.TaskBuilder;
import org.jvnet.hudson.reactor.TaskGraphBuilder;

/**
 * Various key milestone in the termination process of Jenkins.
 *
 * <p>
 * Plugins can use these milestones to execute their tear down processing at the right moment
 * (in addition to defining their own milestones by implementing {@link Milestone}.
 *
 * <p>
 * These milestones are achieve in this order.
 *
 * @author Kohsuke Kawaguchi
 */
public enum TermMilestone implements Milestone {
    /**
     * The very first milestone that gets achieved without doing anything.
     *
     * This is used in {@link Initializer#after()} since annotations cannot have null as the default value.
     */
    STARTED("Started termination"),

    /**
     * The very last milestone
     *
     * This is used in {@link Initializer#before()} since annotations cannot have null as the default value.
     */
    COMPLETED("Completed termination");

    private final String message;

    TermMilestone(String message) {
        this.message = message;
    }

    /**
     * Creates a set of dummy tasks to enforce ordering among {@link TermMilestone}s.
     */
    public static TaskBuilder ordering() {
        TaskGraphBuilder b = new TaskGraphBuilder();
        TermMilestone[] v = values();
        for (int i=0; i<v.length-1; i++)
            b.add(null, Executable.NOOP).requires(v[i]).attains(v[i+1]);
        return b;
    }


    @Override
    public String toString() {
        return message;
    }
}
