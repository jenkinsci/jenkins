package hudson.model;

import hudson.model.Fingerprint.BuildPtr;
import hudson.model.Fingerprint.RangeSet;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapper.Environment;
import hudson.tasks.Builder;
import hudson.tasks.Fingerprinter.FingerprintAction;
import hudson.tasks.Publisher;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.triggers.SCMTrigger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * A build of a {@link Project}.
 *
 * @author Kohsuke Kawaguchi
 */
public final class Build extends AbstractBuild<Project,Build> {

    /**
     * Creates a new build.
     */
    Build(Project project) throws IOException {
        super(project);
    }

    /**
     * Loads a build from a log file.
     */
    Build(Project project, File buildDir) throws IOException {
        super(project,buildDir);
    }

    public Calendar due() {
        return timestamp;
    }

    /**
     * Gets {@link AbstractTestResultAction} associated with this build if any.
     */
    public AbstractTestResultAction getTestResultAction() {
        return getAction(AbstractTestResultAction.class);
    }

    @Override
    public RangeSet getDownstreamRelationship(AbstractProject that) {
        RangeSet rs = new RangeSet();

        FingerprintAction f = getAction(FingerprintAction.class);
        if(f==null)     return rs;

        // look for fingerprints that point to this build as the source, and merge them all
        for (Fingerprint e : f.getFingerprints().values()) {
            BuildPtr o = e.getOriginal();
            if(o!=null && o.is(this))
                rs.add(e.getRangeSet(that));
        }

        return rs;
    }

    @Override
    public int getUpstreamRelationship(AbstractProject that) {
        FingerprintAction f = getAction(FingerprintAction.class);
        if(f==null)     return -1;

        int n = -1;

        // look for fingerprints that point to the given project as the source, and merge them all
        for (Fingerprint e : f.getFingerprints().values()) {
            BuildPtr o = e.getOriginal();
            if(o!=null && o.is(that))
                n = Math.max(n,o.getNumber());
        }

        return n;
    }

    /**
     * During the build this field remembers {@link Environment}s created by
     * {@link BuildWrapper}. This design is bit ugly but forced due to compatibility.
     */
    private transient List<Environment> buildEnvironments;

    @Override
    protected void onStartBuilding() {
        SCMTrigger t = (SCMTrigger)project.getTriggers().get(SCMTrigger.DESCRIPTOR);
        if(t==null) {
            super.onStartBuilding();
        } else {
            synchronized(t) {
                try {
                    t.abort();
                } catch (InterruptedException e) {
                    // handle the interrupt later
                    Thread.currentThread().interrupt();
                }
                super.onStartBuilding();
            }
        }
    }

    @Override
    protected void onEndBuilding() {
        SCMTrigger t = (SCMTrigger)project.getTriggers().get(SCMTrigger.DESCRIPTOR);
        if(t==null) {
            super.onEndBuilding();
        } else {
            synchronized(t) {
                super.onEndBuilding();
                t.startPolling();
            }
        }
    }

    @Override
    public Map<String,String> getEnvVars() {
        Map<String,String> env = super.getEnvVars();

        if(buildEnvironments!=null) {
            for (Environment e : buildEnvironments)
                e.buildEnvVars(env);
        }

        return env;
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
    
    private class RunnerImpl extends AbstractRunner {
        protected Result doRun(BuildListener listener) throws Exception {
            if(!preBuild(listener,project.getBuilders()))
                return Result.FAILURE;
            if(!preBuild(listener,project.getPublishers()))
                return Result.FAILURE;

            buildEnvironments = new ArrayList<Environment>();
            try {
                for( BuildWrapper w : project.getBuildWrappers().values() ) {
                    Environment e = w.setUp(Build.this, launcher, listener);
                    if(e==null)
                        return Result.FAILURE;
                    buildEnvironments.add(e);
                }


                if(!build(listener,project.getBuilders()))
                    return Result.FAILURE;
            } finally {
                // tear down in reverse order
                for( int i=buildEnvironments.size()-1; i>=0; i-- )
                    buildEnvironments.get(i).tearDown(Build.this,listener);
                buildEnvironments = null;
            }

            return null;
        }

        public void post(BuildListener listener) {
            // run all of them even if one of them failed
            try {
                for( Publisher bs : project.getPublishers().values() )
                    bs.perform(Build.this, launcher, listener);
            } catch (InterruptedException e) {
                e.printStackTrace(listener.fatalError("aborted"));
                setResult(Result.FAILURE);
            } catch (IOException e) {
                e.printStackTrace(listener.fatalError("failed"));
                setResult(Result.FAILURE);
            }
        }

        private boolean build(BuildListener listener, Map<?, Builder> steps) throws IOException, InterruptedException {
            for( Builder bs : steps.values() )
                if(!bs.perform(Build.this, launcher, listener))
                    return false;
            return true;
        }

        private boolean preBuild(BuildListener listener,Map<?,? extends BuildStep> steps) {
            for( BuildStep bs : steps.values() )
                if(!bs.prebuild(Build.this,listener))
                    return false;
            return true;
        }
    }
}
