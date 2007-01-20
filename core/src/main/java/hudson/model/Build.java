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
import java.util.Collections;
import java.util.HashMap;
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

    /**
     * Gets the dependency relationship from this build (as the sink)
     * and that project (as the source.)
     *
     * @return
     *      Build number of the upstream build that feed into this build,
     *      or -1 if no record is available.
     */
    public int getUpstreamRelationship(Project that) {
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
     * Gets the upstream builds of this build, which are the builds of the
     * upstream projects whose artifacts feed into this build.
     */
    public Map<Project,Integer> getUpstreamBuilds() {
        Map<Project,Integer> r = new HashMap<Project,Integer>();
        for (Project p : getParent().getUpstreamProjects()) {
            int n = getUpstreamRelationship(p);
            if(n>=0)
                r.put(p,n);
        }
        return r;
    }

    /**
     * Gets the changes in the dependency between the given build and this build.
     */
    public Map<Project,DependencyChange> getDependencyChanges(Build from) {
        if(from==null)             return Collections.emptyMap(); // make it easy to call this from views
        FingerprintAction n = this.getAction(FingerprintAction.class);
        FingerprintAction o = from.getAction(FingerprintAction.class);
        if(n==null || o==null)     return Collections.emptyMap();

        Map<Project,Integer> ndep = n.getDependencies();
        Map<Project,Integer> odep = o.getDependencies();

        Map<Project,DependencyChange> r = new HashMap<Project,DependencyChange>();

        for (Map.Entry<Project,Integer> entry : odep.entrySet()) {
            Project p = entry.getKey();
            Integer oldNumber = entry.getValue();
            Integer newNumber = ndep.get(p);
            if(newNumber!=null && oldNumber.compareTo(newNumber)<0) {
                r.put(p,new DependencyChange(p,oldNumber,newNumber));
            }
        }

        return r;
    }

    /**
     * Represents a change in the dependency.
     */
    public static final class DependencyChange {
        /**
         * The dependency project.
         */
        public final Project project;
        /**
         * Version of the dependency project used in the previous build.
         */
        public final int fromId;
        /**
         * {@link Build} object for {@link #fromId}. Can be null if the log is gone.
         */
        public final Build from;
        /**
         * Version of the dependency project used in this build.
         */
        public final int toId;

        public final Build to;

        public DependencyChange(Project project, int fromId, int toId) {
            this.project = project;
            this.fromId = fromId;
            this.toId = toId;
            this.from = project.getBuildByNumber(fromId);
            this.to = project.getBuildByNumber(toId);
        }
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
