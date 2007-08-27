package hudson.maven;

import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.StreamBuildListener;
import hudson.util.NullStream;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serializable;

/**
 * Delegating {@link BuildListener} that can have "side" {@link OutputStream}
 * that gets log outputs. The side stream can be changed at runtime.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.133
 */
final class SplittableBuildListener implements BuildListener, Serializable {
    /**
     * The actual {@link BuildListener} where the output goes.
     */
    private final BuildListener core;

    private OutputStream side = new NullStream();

    /**
     * Constant {@link PrintStream} connected to both {@link #core} and {@link #side}.
     * This is so that we can change the side stream without the client noticing it.
     */
    private final PrintStream logger;

    public SplittableBuildListener(BuildListener core) {
        this.core = core;
        final OutputStream base = core.getLogger();
        logger = new PrintStream(new OutputStream() {
            public void write(int b) throws IOException {
                base.write(b);
                side.write(b);
            }

            public void write(byte b[], int off, int len) throws IOException {
                base.write(b,off,len);
                side.write(b,off,len);
            }

            public void flush() throws IOException {
                base.flush();
                side.flush();
            }

            public void close() throws IOException {
                base.close();
                side.close();
            }
        });
    }
    
    public void setSideOutputStream(OutputStream os) {
        if(os==null)    os = new NullStream();
        this.side = os;
    }

    public void started() {
        core.started();
    }

    public void finished(Result result) {
        core.finished(result);
    }

    public PrintStream getLogger() {
        return logger;
    }

    public PrintWriter error(String msg) {
        core.error(msg);
        return new PrintWriter(logger);
    }

    public PrintWriter fatalError(String msg) {
        core.fatalError(msg);
        return new PrintWriter(logger);
    }

    private Object writeReplace() {
        return new StreamBuildListener(logger);
    }

    private static final long serialVersionUID = 1L;
}
