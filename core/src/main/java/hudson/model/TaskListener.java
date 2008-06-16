package hudson.model;

import hudson.util.NullStream;
import hudson.util.StreamTaskListener;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Formatter;

/**
 * Receives events that happen during some task execution,
 * such as a build or SCM change polling.
 *
 * <p>
 * This interface is implemented by Hudson core and passed to extension points so that
 * they can record the progress of the build without really knowing how those information
 * and handled/stored by Hudson.
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
     * {@link Formatter#format(String, Object[])} version of {@link #error(String)}.
     */
    PrintWriter error(String format, Object... args);

    /**
     * A fatal error in the build.
     *
     * @return
     *      A writer to receive details of the error. Not null.
     */
    PrintWriter fatalError(String msg);

    /**
     * {@link Formatter#format(String, Object[])} version of {@link #fatalError(String)}.
     */
    PrintWriter fatalError(String format, Object... args);

    /**
     * {@link TaskListener} that discards the output.
     */
    public static final TaskListener NULL = new StreamTaskListener(new NullStream());
}
