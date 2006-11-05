package hudson.model;

import hudson.util.StreamTaskListener;
import hudson.util.NullStream;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * Receives events that happen during some task execution,
 * such as a build or SCM change polling.
 *
 * @author Kohsuke Kawaguchi
 */
public interface TaskListener {
    /**
     * This writer will receive the output of the build.
     *
     * @return
     *      must be non-null.
     */
    PrintStream getLogger();

    /**
     * An error in the build.
     *
     * @return
     *      A writer to receive details of the error. Not null.
     */
    PrintWriter error(String msg);

    /**
     * A fatal error in the build.
     *
     * @return
     *      A writer to receive details of the error. Not null.
     */
    PrintWriter fatalError(String msg);

    /**
     * {@link TaskListener} that discards the output.
     */
    public static final TaskListener NULL = new StreamTaskListener(new NullStream());
}
