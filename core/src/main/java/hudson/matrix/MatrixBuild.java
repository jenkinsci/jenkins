package hudson.matrix;

import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Calendar;
import java.util.Collection;
import java.util.logging.Logger;

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
        protected Result doRun(BuildListener listener) throws Exception {
            MatrixProject p = getProject();
            PrintStream logger = listener.getLogger();

            Collection<MatrixConfiguration> activeConfigurations = p.getActiveConfigurations();

            for(MatrixConfiguration c : activeConfigurations) {
                logger.println("Triggering "+c.getName());
                c.scheduleBuild();
            }

            // this occupies an executor unnecessarily.
            // it would be nice if this can be placed in a temproary executor.

            Result r = Result.SUCCESS;
            int n = getNumber();
            for (MatrixConfiguration c : activeConfigurations) {
                // wait for the completion
                while(true) {
                    MatrixRun b = c.getBuildByNumber(n);
                    if(b!=null && !b.isBuilding()) {
                        r = r.combine(b.getResult());
                        break;
                    }
                    Thread.sleep(1000);
                }
            }

            return r;
        }

        public void post(BuildListener listener) {
            // TODO: run aggregators
        }
    }

    private static final Logger LOGGER = Logger.getLogger(MatrixBuild.class.getName());
}
