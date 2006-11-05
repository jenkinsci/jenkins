package hudson.util;

import hudson.model.TaskListener;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;

/**
 * {@link TaskListener} that generates output into a single stream.
 *
 * @author Kohsuke Kawaguchi
 */
public final class StreamTaskListener implements TaskListener {
    private final PrintStream out;

    public StreamTaskListener(PrintStream out) {
        this.out = out;
    }

    public StreamTaskListener(OutputStream out) {
        this(new PrintStream(out));
    }

    public StreamTaskListener(Writer w) {
        this(new WriterOutputStream(w));
    }

    public PrintStream getLogger() {
        return out;
    }

    public PrintWriter error(String msg) {
        out.println(msg);
        return new PrintWriter(new OutputStreamWriter(out),true);
    }

    public PrintWriter fatalError(String msg) {
        return error(msg);
    }
}
