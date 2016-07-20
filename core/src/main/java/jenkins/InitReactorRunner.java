package jenkins;

import jenkins.util.SystemProperties;
import hudson.init.InitMilestone;
import hudson.init.InitReactorListener;
import hudson.util.DaemonThreadFactory;
import hudson.util.NamingThreadFactory;
import hudson.util.Service;
import jenkins.model.Configuration;
import jenkins.model.Jenkins;
import org.jvnet.hudson.reactor.Milestone;
import org.jvnet.hudson.reactor.Reactor;
import org.jvnet.hudson.reactor.ReactorException;
import org.jvnet.hudson.reactor.ReactorListener;
import org.jvnet.hudson.reactor.Task;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.SEVERE;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Executes the {@link Reactor} for the purpose of bootup.
 *
 * @author Kohsuke Kawaguchi
 */
public class InitReactorRunner {
    public void run(Reactor reactor) throws InterruptedException, ReactorException, IOException {
         reactor.addAll(InitMilestone.ordering().discoverTasks(reactor));

        ExecutorService es;
        if (Jenkins.PARALLEL_LOAD)
            es = new ThreadPoolExecutor(
                TWICE_CPU_NUM, TWICE_CPU_NUM, 5L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(), new DaemonThreadFactory());
        else
            es = Executors.newSingleThreadExecutor(new NamingThreadFactory(new DaemonThreadFactory(), "InitReactorRunner"));
        try {
            reactor.execute(es,buildReactorListener());
        } finally {
            es.shutdownNow();   // upon a successful return the executor queue should be empty. Upon an exception, we want to cancel all pending tasks
        }

    }

    /**
     * Aggregates all the listeners into one and returns it.
     *
     * <p>
     * At this point plugins are not loaded yet, so we fall back to the META-INF/services look up to discover implementations.
     * As such there's no way for plugins to participate into this process.
     */
    private ReactorListener buildReactorListener() throws IOException {
        List<ReactorListener> r = (List) Service.loadInstances(Thread.currentThread().getContextClassLoader(), InitReactorListener.class);
        r.add(new ReactorListener() {
            final Level level = Level.parse( Configuration.getStringConfigParameter("initLogLevel", "FINE") );
            public void onTaskStarted(Task t) {
                LOGGER.log(level, "Started {0}", getDisplayName(t));
            }

            public void onTaskCompleted(Task t) {
                LOGGER.log(level, "Completed {0}", getDisplayName(t));
            }

            public void onTaskFailed(Task t, Throwable err, boolean fatal) {
                LOGGER.log(SEVERE, "Failed " + getDisplayName(t), err);
            }

            public void onAttained(Milestone milestone) {
                Level lv = level;
                String s = "Attained "+milestone.toString();
                if (milestone instanceof InitMilestone) {
                    lv = Level.INFO; // noteworthy milestones --- at least while we debug problems further
                    onInitMilestoneAttained((InitMilestone) milestone);
                    s = milestone.toString();
                }
                LOGGER.log(lv,s);
            }
        });
        return new ReactorListener.Aggregator(r);
    }

    /** Like {@link Task#getDisplayName} but more robust. */
    @Restricted(NoExternalUse.class)
    public static String getDisplayName(Task t) {
        try {
            return t.getDisplayName();
        } catch (RuntimeException | Error x) {
            LOGGER.log(Level.WARNING, "failed to find displayName of " + t, x);
            return t.toString();
        }
    }

    /**
     * Called when the init milestone is attained.
     */
    protected void onInitMilestoneAttained(InitMilestone milestone) {
    }

    private static final int TWICE_CPU_NUM = SystemProperties.getInteger(
            InitReactorRunner.class.getName()+".concurrency",
            Runtime.getRuntime().availableProcessors() * 2);

    private static final Logger LOGGER = Logger.getLogger(InitReactorRunner.class.getName());
}
