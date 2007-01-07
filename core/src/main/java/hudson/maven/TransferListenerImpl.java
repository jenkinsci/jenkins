package hudson.maven;

import hudson.model.TaskListener;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;

import java.io.PrintStream;

/**
 * {@link TransferListener} implementation.
 *
 * <p>
 * This implementation puts the transfer progress indication in a distinctively formatted line,
 * so that on HTML we can render the progress as a progress bar.
 *
 * @author Kohsuke Kawaguchi
 */
final class TransferListenerImpl implements TransferListener {
    /**
     * Receives the formatter messages.
     */
    private final PrintStream out;

    private long transferedSize;


    public TransferListenerImpl(TaskListener listener) {
        this.out = listener.getLogger();
    }

    public void transferInitiated( TransferEvent e )
    {
        String url = e.getWagon().getRepository().getUrl();

        if(e.getRequestType()==TransferEvent.REQUEST_PUT) {
            out.println("Uploading to "+url);
        } else {
            out.println("Downloading "+url);
        }
    }

    public void transferStarted(TransferEvent e) {
        transferedSize = 0;
        long total = e.getResource().getContentLength();
        out.println(HEADER+" start "+total);
    }

    public void transferProgress(TransferEvent e, byte[] buffer, int length) {
        transferedSize += length;
        out.println(HEADER + " progress " + transferedSize);
    }

    public void transferCompleted(TransferEvent e) {
        out.println(HEADER + " completed");
    }

    public void transferError(TransferEvent e) {
        out.println(HEADER + " error");
        e.getException().printStackTrace(out);
    }

    public void debug(String message) {
    }

    /**
     * Lines printed by this class will have this header. 
     */
    public static final String HEADER = "[:TRANSFER:]";
}
