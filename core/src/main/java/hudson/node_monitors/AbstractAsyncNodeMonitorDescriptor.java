package hudson.node_monitors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.WARNING;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Functions;
import hudson.model.Computer;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.slaves.SlaveComputer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

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
    protected abstract @CheckForNull Callable<T, IOException> createCallable(Computer c);

    @Override
    protected T monitor(Computer c) throws IOException, InterruptedException {
        VirtualChannel ch = c.getChannel();
        if (ch != null) {
            Callable<T, IOException> cc = createCallable(c);
            if (cc != null)
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
    protected final @NonNull Result<T> monitorDetailed() throws InterruptedException {
        Map<Computer, Future<T>> futures = new HashMap<>();
        Set<Computer> skipped = new HashSet<>();

        for (Computer c : Jenkins.get().getComputers()) {
            try {
                VirtualChannel ch = c.getChannel();
                futures.put(c, null);    // sentinel value
                if (ch != null) {
                    Callable<T, ?> cc = createCallable(c);
                    if (cc != null)
                        futures.put(c, ch.callAsync(cc));
                }
            } catch (RuntimeException | IOException e) {
                error(c, e);
            }
        }

        final long now = System.currentTimeMillis();
        final long end = now + getMonitoringTimeOut();

        final Map<Computer, T> data = new HashMap<>();

        for (Map.Entry<Computer, Future<T>> e : futures.entrySet()) {
            Computer c = e.getKey();
            Future<T> f = futures.get(c);
            data.put(c, null);  // sentinel value

            if (f != null) {
                try {
                    data.put(c, f.get(Math.max(0, end - System.currentTimeMillis()), MILLISECONDS));
                } catch (RuntimeException | TimeoutException | ExecutionException x) {
                    error(c, x);
                }
            } else {
                skipped.add(c);
            }
        }

        return new Result<>(data, skipped);
    }

    private void error(Computer c, Throwable x) {
        // JENKINS-54496: don't log if c was removed from Jenkins after we'd started monitoring
        final boolean cIsStillCurrent = Jenkins.get().getComputer(c.getName()) == c;
        if (!cIsStillCurrent) {
            return;
        }
        if (c instanceof SlaveComputer) {
            Functions.printStackTrace(x, ((SlaveComputer) c).getListener().error("Failed to monitor for " + getDisplayName()));
        } else {
            LOGGER.log(WARNING, "Failed to monitor " + c.getDisplayName() + " for " + getDisplayName(), x);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(AbstractAsyncNodeMonitorDescriptor.class.getName());

    /**
     * Result object for {@link AbstractAsyncNodeMonitorDescriptor#monitorDetailed()} to facilitate extending information
     * returned in the future.
     * <p>
     * The {@link #getMonitoringData()} provides the results of the monitoring as {@link #monitor()} does. Note the value
     * in the map can be {@code null} for several reasons:
     * <ul>
     *     <li>The monitoring {@link Callable} returned {@code null} as a provisioning result.</li>
     *     <li>Creating or evaluating that callable has thrown an exception.</li>
     *     <li>The computer was not monitored as it was offline.</li>
     *     <li>The {@link AbstractAsyncNodeMonitorDescriptor#createCallable} has returned null.</li>
     * </ul>
     *
     * Clients can distinguish among these states based on the additional data attached to this object. {@link #getSkipped()}
     * returns computers that were not monitored as they were either offline or monitor produced {@code null} {@link Callable}.
     */
    protected static final class Result<T> {
        private static final long serialVersionUID = -7671448355804481216L;

        private final @NonNull Map<Computer, T> data;
        private final @NonNull ArrayList<Computer> skipped;

        private Result(@NonNull Map<Computer, T> data, @NonNull Collection<Computer> skipped) {
            this.data = new HashMap<>(data);
            this.skipped = new ArrayList<>(skipped);
        }

        public @NonNull Map<Computer, T> getMonitoringData() {
            return data;
        }

        /**
         * Computers that were skipped during monitoring as they either do not have a channel (offline) or the monitor
         * has not produced the Callable. Computers that caused monitor to throw exception are not returned here.
         */
        public @NonNull List<Computer> getSkipped() {
            return skipped;
        }
    }
}
