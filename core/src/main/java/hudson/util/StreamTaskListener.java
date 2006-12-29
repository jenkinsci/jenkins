package hudson.util;

import hudson.model.TaskListener;
import hudson.remoting.RemoteOutputStream;
import hudson.CloseProofOutputStream;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;

/**
 * {@link TaskListener} that generates output into a single stream.
 *
 * <p>
 * This object is remotable.
 * 
 * @author Kohsuke Kawaguchi
 */
public final class StreamTaskListener implements TaskListener, Serializable {
    private PrintStream out;

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

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.writeObject(new RemoteOutputStream(new CloseProofOutputStream(this.out)));
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        out = (PrintStream) in.readObject();
    }

    private static final long serialVersionUID = 1L;
}
