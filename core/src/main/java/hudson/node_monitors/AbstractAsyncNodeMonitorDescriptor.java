package hudson.node_monitors;

import hudson.model.Computer;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.WARNING;

/**
 * Sophisticated version of {@link AbstractNodeMonitorDescriptor} that
 * performs monitoring on all agents concurrently and asynchronously.
 *
 * @param <T>
 *     represents the result of the monitoring.
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
     *
     * @return Mapping from computer to monitored value. The map values can be null for several reasons, see {@link Result}
     * for more details.
     */
    @Override
    protected Map<Computer, T> monitor() throws InterruptedException {
        // Bridge method to offer original constrained interface.
        return monitorDetailed().getMonitoringData();
    }

    /**
     * Perform monitoring with detailed reporting.
     */
    protected final @Nonnull Result<T> monitorDetailed() throws InterruptedException {
        Map<Computer,Future<T>> futures = new HashMap<Computer,Future<T>>();
        Set<Computer> skipped = new HashSet<>();

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
            } else {
                skipped.add(c);
            }
        }

        return new Result<>(data, skipped);
    }

    private static final Logger LOGGER = Logger.getLogger(AbstractAsyncNodeMonitorDescriptor.class.getName());

    /**
     * Result object for {@link AbstractAsyncNodeMonitorDescriptor#monitorDetailed()} to facilitate extending information
     * returned in the future.
     *
     * The {@link #getMonitoringData()} provides the results of the monitoring as {@link #monitor()} does. Note the value
     * in the map can be <tt>null</tt> for several reasons:
     * <ul>
     *     <li>The monitoring {@link Callable} returned <tt>null</tt> as a provisioning result.</li>
     *     <li>Creating or evaluating that callable has thrown an exception.</li>
     *     <li>The computer was not monitored as it was offline.</li>
     *     <li>The {@link AbstractAsyncNodeMonitorDescriptor#createCallable} has returned null.</li>
     * </ul>
     *
     * Clients can distinguishing among these states based on the additional data attached to this object. {@link #getSkipped()}
     * returns computers that was not monitored as they ware either offline or monitor produced <tt>null</tt> {@link Callable}.
     */
    protected static final class Result<T> {
        private static final long serialVersionUID = -7671448355804481216L;

        private final @Nonnull Map<Computer, T> data;
        private final @Nonnull ArrayList<Computer> skipped;

        private Result(@Nonnull Map<Computer, T> data, @Nonnull Collection<Computer> skipped) {
            this.data = new HashMap<>(data);
            this.skipped = new ArrayList<>(skipped);
        }

        protected @Nonnull Map<Computer, T> getMonitoringData() {
            return data;
        }

        /**
         * Computers that ware skipped during monitoring as they either do not have a a channel (offline) or the monitor
         * have not produced the Callable. Computers that caused monitor to throw exception are not returned here.
         */
        protected @Nonnull List<Computer> getSkipped() {
            return skipped;
        }
    }
}
