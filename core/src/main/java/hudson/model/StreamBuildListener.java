package hudson.model;

import hudson.util.WriterOutputStream;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;

/**
 * {@link BuildListener} that writes to a {@link Writer}.
 * @author Kohsuke Kawaguchi
 */
public class StreamBuildListener implements BuildListener {
    private final PrintWriter w;

    private final PrintStream ps;

    public StreamBuildListener(Writer w) {
        this(new PrintWriter(w));
    }

    public StreamBuildListener(PrintWriter w) {
        this.w = w;
        // unless we auto-flash, PrintStream will use BufferedOutputStream internally,
        // and break ordering
        this.ps = new PrintStream(new WriterOutputStream(w),true);
    }

    public void started() {
        w.println("started");
    }

    public PrintStream getLogger() {
        return ps;
    }

    public PrintWriter error(String msg) {
        w.println("ERROR: "+msg);
        return w;
    }

    public PrintWriter fatalError(String msg) {
        w.println("FATAL: "+msg);
        return w;
    }

    public void finished(Result result) {
        w.println("finished: "+result);
    }
}
