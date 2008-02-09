package hudson.matrix;

import hudson.model.*;
import hudson.tasks.Publisher;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;

/**
 * Build of {@link MatrixProject}.
 *
 * @author Kohsuke Kawaguchi
 */
public class MatrixBuild extends AbstractBuild<MatrixProject,MatrixBuild> {
    public MatrixBuild(MatrixProject job) throws IOException {
        super(job);
    }

    public MatrixBuild(MatrixProject job, Calendar timestamp) {
        super(job, timestamp);
    }

    public MatrixBuild(MatrixProject project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    public Layouter<MatrixRun> getLayouter() {
        return new Layouter<MatrixRun>(getParent().getAxes()) {
            protected MatrixRun getT(Combination c) {
                return getRun(c);
            }
        };
    }

    /**
     * Gets the {@link MatrixRun} in this build that corresponds
     * to the given combination.
     */
    public MatrixRun getRun(Combination c) {
        MatrixConfiguration config = getParent().getItem(c);
        if(config==null)    return null;
        return config.getBuildByNumber(getNumber());
    }

    public Object getDynamic(String token, StaplerRequest req, StaplerResponse rsp) {
        try {
            MatrixRun item = getRun(Combination.fromString(token));
            if(item!=null)
            return item;
        } catch (IllegalArgumentException _) {
            // failed to parse the token as Combination. Must be something else
        }
        return super.getDynamic(token,req,rsp);
    }

    @Override
    public void run() {
        run(new RunnerImpl());
    }

    private class RunnerImpl extends AbstractRunner {
        private final List<MatrixAggregator> aggregators = new ArrayList<MatrixAggregator>();

        protected Result doRun(BuildListener listener) throws Exception {
            MatrixProject p = getProject();
            PrintStream logger = listener.getLogger();

            // list up aggregators
            for (Publisher pub : p.getPublishers().values()) {
                if (pub instanceof MatrixAggregatable) {
                    MatrixAggregatable ma = (MatrixAggregatable) pub;
                    MatrixAggregator a = ma.createAggregator(MatrixBuild.this, launcher, listener);
                    if(a!=null)
                        aggregators.add(a);
                }
            }

            Collection<MatrixConfiguration> activeConfigurations = p.getActiveConfigurations();
            int n = getNumber();

            for (MatrixAggregator a : aggregators)
                if(!a.startBuild())
                    return Result.FAILURE;

            try {
                for(MatrixConfiguration c : activeConfigurations) {
                    logger.println(Messages.MatrixBuild_Triggering(c.getName()));
                    c.scheduleBuild();
                }

                // this occupies an executor unnecessarily.
                // it would be nice if this can be placed in a temproary executor.

                Result r = Result.SUCCESS;
                for (MatrixConfiguration c : activeConfigurations) {
                    // wait for the completion
                    while(true) {
                        MatrixRun b = c.getBuildByNumber(n);

                        // two ways to get beyond this. one is that the build starts and gets done,
                        // or the build gets cancelled before it even started.
                        Result buildResult = null;
                        if(b!=null && !b.isBuilding())
                            buildResult = b.getResult();
                        if(b==null && !c.isInQueue()) {
                            // there's conceivably a race condition here, sine b is set early on,
                            // and we are checking c.isInQueue() later. A build might have started
                            // after we computed b but before we checked c.isInQueue(). So
                            // double-check 'b' to see if it's really not there. Possibly related to
                            // http://www.nabble.com/Master-slave-problem-tt14710987.html
                            b = c.getBuildByNumber(n);
                            if(b==null) {
                                logger.println(Messages.MatrixBuild_AppearsCancelled(c.getDisplayName()));
                                buildResult = Result.ABORTED;
                            }
                        }

                        if(buildResult!=null) {
                            r = r.combine(buildResult);
                            if(b!=null)
                                for (MatrixAggregator a : aggregators)
                                    if(!a.endRun(b))
                                        return Result.FAILURE;
                            break;
                        }
                        Thread.sleep(1000);
                    }
                }

                return r;
            } finally {
                // if the build was aborted in the middle. Cancel all the configuration builds.
                Queue q = Hudson.getInstance().getQueue();
                synchronized(q) {// avoid micro-locking in q.cancel.
                    for (MatrixConfiguration c : activeConfigurations) {
                        if(q.cancel(c))
                            logger.println(Messages.MatrixBuild_Cancelled(c.getDisplayName()));
                        MatrixRun b = c.getBuildByNumber(n);
                        if(b!=null) {
                            Executor exe = b.getExecutor();
                            if(exe!=null) {
                                logger.println(Messages.MatrixBuild_Interrupting(b.getDisplayName()));
                                exe.interrupt();
                            }
                        }
                    }
                }
            }
        }

        public void post2(BuildListener listener) throws Exception {
            for (MatrixAggregator a : aggregators)
                a.endBuild();
        }
    }
}
