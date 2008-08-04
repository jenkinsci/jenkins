package hudson.node_monitors;

import hudson.Util;
import hudson.model.Computer;
import hudson.remoting.Callable;
import hudson.remoting.Future;
import hudson.util.TimeUnit2;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * Monitors the round-trip response time to this slave.
 *
 * @author Kohsuke Kawaguchi
 */
public class ResponseTimeMonitor extends NodeMonitor {
    /**
     * Returns the HTML representation of the result.
     */
    public String toHtml(long ms) {
        if(ms<0)
            return Util.wrapToErrorSpan("Time out");
        return ms+"ms";
    }

    public AbstractNodeMonitorDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public static final AbstractNodeMonitorDescriptor<Long> DESCRIPTOR = new AbstractNodeMonitorDescriptor<Long>(ResponseTimeMonitor.class) {
        protected Long monitor(Computer c) throws IOException, InterruptedException {
            long start = System.nanoTime();
            Future<String> f = c.getChannel().callAsync(new NoopTask());
            try {
                f.get(5, TimeUnit.SECONDS);
                long end = System.nanoTime();
                return TimeUnit2.NANOSECONDS.toMillis(end-start);
            } catch (ExecutionException e) {
                throw new IOException(e.getCause());    // I don't think this is possible
            } catch (TimeoutException e) {
                // TODO: this scheme should be generalized, so that Hudson can remember why it's marking the node
                // as offline, as well as allowing the user to force Hudson to use it.
                if(!c.isTemporarilyOffline()) {
                    LOGGER.warning("Making "+c.getName()+" offline temporarily because it's not responding");
                    c.setTemporarilyOffline(true);
                }
                // special constant to indicate that the processing timed out.
                return -1L;
            }
        }

        public String getDisplayName() {
            return "Response Time";
        }

        public NodeMonitor newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return new ResponseTimeMonitor();
        }
    };

    private static class NoopTask implements Callable<String,RuntimeException> {
        public String call() {
            return null;
        }

        private static final long serialVersionUID = 1L;
    }

    static {
        LIST.add(DESCRIPTOR);
    }

    private static final Logger LOGGER = Logger.getLogger(ResponseTimeMonitor.class.getName());
}
