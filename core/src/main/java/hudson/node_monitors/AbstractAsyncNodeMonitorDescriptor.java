package hudson.node_monitors;

import hudson.model.Computer;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.WARNING;

/**
 * Sophisticated version of {@link AbstractNodeMonitorDescriptor} that
 * performs monitoring on all slaves concurrently and asynchronously.
 *
 * @param <T>
 *     represents the the result of the monitoring.
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractAsyncNodeMonitorDescriptor<T> extends AbstractNodeMonitorDescriptor<T> {
    protected AbstractAsyncNodeMonitorDescriptor() {
    }

    protected AbstractAsyncNodeMonitorDescriptor(long interval) {
        super(interval);
    }

    protected AbstractAsyncNodeMonitorDescriptor(Class<? extends NodeMonitor> clazz) {
        super(clazz);
    }

    protected AbstractAsyncNodeMonitorDescriptor(Class<? extends NodeMonitor> clazz, long interval) {
        super(clazz, interval);
    }

    /**
     * Creates a {@link Callable} that performs the monitoring when executed.
     */
    protected abstract @CheckForNull Callable<T,IOException> createCallable(Computer c);

    @Override
    protected T monitor(Computer c) throws IOException, InterruptedException {
        VirtualChannel ch = c.getChannel();
        if (ch != null) {
            Callable<T,IOException> cc = createCallable(c);
            if (cc!=null)
                return ch.call(cc);
        }
        return null;
    }

    /**
     * Performs all monitoring concurrently.
     */
    @Override
    protected Map<Computer, T> monitor() throws InterruptedException {
        Map<Computer,Future<T>> futures = new HashMap<Computer,Future<T>>();

        for (Computer c : Jenkins.getInstance().getComputers()) {
            try {
                VirtualChannel ch = c.getChannel();
                futures.put(c,null);    // sentinel value
                if (ch!=null) {
                    Callable<T, ?> cc = createCallable(c);
                    if (cc!=null)
                        futures.put(c,ch.callAsync(cc));
                }
            } catch (RuntimeException e) {
                LOGGER.log(WARNING, "Failed to monitor "+c.getDisplayName()+" for "+getDisplayName(), e);
            } catch (IOException e) {
                LOGGER.log(WARNING, "Failed to monitor "+c.getDisplayName()+" for "+getDisplayName(), e);
            }
        }

        final long now = System.currentTimeMillis();
        final long end = now + getMonitoringTimeOut();

        final Map<Computer,T> data = new HashMap<Computer,T>();

        for (Entry<Computer, Future<T>> e : futures.entrySet()) {
            Computer c = e.getKey();
            Future<T> f = futures.get(c);
            data.put(c, null);  // sentinel value

            if (f!=null) {
                try {
                    data.put(c,f.get(Math.max(0,end-System.currentTimeMillis()), MILLISECONDS));
                } catch (RuntimeException x) {
                    LOGGER.log(WARNING, "Failed to monitor " + c.getDisplayName() + " for " + getDisplayName(), x);
                } catch (ExecutionException x) {
                    LOGGER.log(WARNING, "Failed to monitor " + c.getDisplayName() + " for " + getDisplayName(), x);
                } catch (TimeoutException x) {
                    LOGGER.log(WARNING, "Failed to monitor " + c.getDisplayName() + " for " + getDisplayName(), x);
                }
            }
        }

        return data;
    }

    private static final Logger LOGGER = Logger.getLogger(AbstractAsyncNodeMonitorDescriptor.class.getName());
}
