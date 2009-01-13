package hudson.model;

import hudson.tasks.BuildStep;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapper.Environment;
import hudson.tasks.Builder;
import hudson.tasks.BuildTrigger;
import hudson.triggers.SCMTrigger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Collection;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * A build of a {@link Project}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Build <P extends Project<P,B>,B extends Build<P,B>>
    extends AbstractBuild<P,B> {

    /**
     * If the build required a lock, remember it so that we can release it.
     */
    private transient ReentrantLock buildLock;

    /**
     * Creates a new build.
     */
    protected Build(P project) throws IOException {
        super(project);
    }

    protected Build(P job, Calendar timestamp) {
        super(job, timestamp);
    }

    /**
     * Loads a build from a log file.
     */
    protected Build(P project, File buildDir) throws IOException {
        super(project,buildDir);
    }

    @Override
    protected void onStartBuilding() {
        super.onStartBuilding();
        SCMTrigger t = (SCMTrigger)project.getTriggers().get(SCMTrigger.DESCRIPTOR);
        if(t!=null) {
            // acquire the lock
            buildLock = t.getLock();
            synchronized(buildLock) {
                try {
                    if(buildLock.isLocked()) {
                        long time = System.currentTimeMillis();
                        LOGGER.info("Waiting for the polling of "+getParent()+" to complete");
                        buildLock.lockInterruptibly();
                        LOGGER.info("Polling completed. Waited "+(System.currentTimeMillis()-time)+"ms");
                    } else
                        buildLock.lockInterruptibly();
                } catch (InterruptedException e) {
                    // handle the interrupt later
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @Override
    protected void onEndBuilding() {
        super.onEndBuilding();
        if(buildLock!=null)
            buildLock.unlock();
    }

//
//
// actions
//
//
    @Override
    public void run() {
        run(new RunnerImpl());
    }
    
    protected class RunnerImpl extends AbstractRunner {
        protected Result doRun(BuildListener listener) throws Exception {
            if(!preBuild(listener,project.getBuilders()))
                return Result.FAILURE;
            if(!preBuild(listener,project.getPublishers()))
                return Result.FAILURE;

            buildEnvironments = new ArrayList<Environment>();
            try {
                List<BuildWrapper> wrappers = new ArrayList<BuildWrapper>(project.getBuildWrappers().values());

                ParametersAction parameters = getAction(ParametersAction.class);
                if (parameters != null)
                    parameters.createBuildWrappers(Build.this,wrappers);

                for( BuildWrapper w : wrappers ) {
                    Environment e = w.setUp((AbstractBuild)Build.this, launcher, listener);
                    if(e==null)
                        return Result.FAILURE;
                    buildEnvironments.add(e);
                }

                if(!build(listener,project.getBuilders()))
                    return Result.FAILURE;
            } finally {
                // tear down in reverse order
                boolean failed=false;
                for( int i=buildEnvironments.size()-1; i>=0; i-- ) {
                    if (!buildEnvironments.get(i).tearDown((AbstractBuild)Build.this,listener)) {
                        failed=true;
                    }                    
                }
                buildEnvironments = null;
                // WARNING The return in the finally clause will trump any return before
                if (failed) return Result.FAILURE;
            }

            return null;
        }

        public void post2(BuildListener listener) throws IOException, InterruptedException {
            performAllBuildStep(listener, project.getPublishers(),true);
            performAllBuildStep(listener, project.getProperties(),true);
        }

        public void cleanUp(BuildListener listener) throws Exception {
            performAllBuildStep(listener, project.getPublishers(),false);
            performAllBuildStep(listener, project.getProperties(),false);
            BuildTrigger.execute(Build.this,listener, (BuildTrigger)project.getPublisher(BuildTrigger.DESCRIPTOR));
        }

        private boolean build(BuildListener listener, Collection<Builder> steps) throws IOException, InterruptedException {
            for( BuildStep bs : steps )
                if(!bs.perform(Build.this, launcher, listener))
                    return false;
            return true;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(Build.class.getName());
}
