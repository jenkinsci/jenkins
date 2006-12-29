package hudson.model;

import hudson.Launcher;
import hudson.Util;
import hudson.Proc.LocalProc;
import hudson.model.Fingerprint.BuildPtr;
import hudson.model.Fingerprint.RangeSet;
import static hudson.model.Hudson.isWindows;
import hudson.scm.CVSChangeLogParser;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.scm.SCM;
import hudson.tasks.BuildStep;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapper.Environment;
import hudson.tasks.Builder;
import hudson.tasks.Fingerprinter.FingerprintAction;
import hudson.tasks.Publisher;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.triggers.SCMTrigger;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.xml.sax.SAXException;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Kohsuke Kawaguchi
 */
public final class Build extends Run<Project,Build> implements Runnable {

    /**
     * Name of the slave this project was built on.
     * Null if built by the master.
     */
    private String builtOn;

    /**
     * SCM used for this build.
     * Maybe null, for historical reason, in which case CVS is assumed.
     */
    private ChangeLogParser scm;

    /**
     * Changes in this build.
     */
    private volatile transient ChangeLogSet<? extends Entry> changeSet;

    /**
     * Creates a new build.
     */
    Build(Project project) throws IOException {
        super(project);
    }

    public Project getProject() {
        return getParent();
    }

    /**
     * Loads a build from a log file.
     */
    Build(Project project, File buildDir) throws IOException {
        super(project,buildDir);
    }

    @Override
    public String getWhyKeepLog() {
        // if any of the downstream project is configured with 'keep dependency component',
        // we need to keep this log
        for (Map.Entry<Project, RangeSet> e : getDownstreamBuilds().entrySet()) {
            Project p = e.getKey();
            if(!p.isKeepDependencies())     continue;

            // is there any active build that depends on us?
            for (Build build : p.getBuilds()) {
                if(e.getValue().includes(build.getNumber()))
                    return "kept because of "+build;
            }
        }

        return super.getWhyKeepLog();
    }

    /**
     * Gets the changes incorporated into this build.
     *
     * @return never null.
     */
    public ChangeLogSet<? extends Entry> getChangeSet() {
        if(scm==null)
            scm = new CVSChangeLogParser();

        if(changeSet==null) // cached value
            changeSet = calcChangeSet();
        return changeSet;
    }

    /**
     * Returns true if the changelog is already computed.
     */
    public boolean hasChangeSetComputed() {
        File changelogFile = new File(getRootDir(), "changelog.xml");
        return changelogFile.exists();
    }

    private ChangeLogSet<? extends Entry> calcChangeSet() {
        File changelogFile = new File(getRootDir(), "changelog.xml");
        if(!changelogFile.exists())
            return ChangeLogSet.EMPTY;

        try {
            return scm.parse(this,changelogFile);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }
        return ChangeLogSet.EMPTY;
    }

    public Calendar due() {
        return timestamp;
    }

    /**
     * Returns a {@link Slave} on which this build was done.
     */
    public Node getBuiltOn() {
        if(builtOn==null)
            return Hudson.getInstance();
        else
            return Hudson.getInstance().getSlave(builtOn);
    }

    /**
     * Returns the name of the slave it was built on, or null if it was the master.
     */
    public String getBuiltOnStr() {
        return builtOn;
    }

    /**
     * Gets {@link AbstractTestResultAction} associated with this build if any.
     */
    public AbstractTestResultAction getTestResultAction() {
        return getAction(AbstractTestResultAction.class);
    }

    /**
     * Gets the dependency relationship from this build (as the source)
     * and that project (as the sink.)
     *
     * @return
     *      range of build numbers that represent which downstream builds are using this build.
     *      The range will be empty if no build of that project matches this.
     */
    public RangeSet getDownstreamRelationship(Project that) {
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
     * Gets the downstream builds of this build, which are the builds of the
     * downstream projects that use artifacts of this build.
     *
     * @return
     *      For each project with fingerprinting enabled, returns the range
     *      of builds (which can be empty if no build uses the artifact from this build.)
     */
    public Map<Project,RangeSet> getDownstreamBuilds() {
        Map<Project,RangeSet> r = new HashMap<Project,RangeSet>();
        for (Project p : getParent().getDownstreamProjects()) {
            if(p.isFingerprintConfigured())
                r.put(p,getDownstreamRelationship(p));
        }
        return r;
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

    /**
     * Performs a build.
     */
    public void run() {
        run(new Runner() {

            /**
             * Since configuration can be changed while a build is in progress,
             * stick to one launcher and use it.
             */
            private Launcher launcher;

            public Result run(BuildListener listener) throws Exception {
                Node node = Executor.currentExecutor().getOwner().getNode();
                assert builtOn==null;
                builtOn = node.getNodeName();

                launcher = node.createLauncher(listener);
                if(node instanceof Slave)
                    listener.getLogger().println("Building remotely on "+node.getNodeName());


                if(!project.checkout(Build.this,launcher,listener,new File(getRootDir(),"changelog.xml")))
                    return Result.FAILURE;

                SCM scm = project.getScm();

                Build.this.scm = scm.createChangeLogParser();
                Build.this.changeSet = Build.this.calcChangeSet();

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

                if(!isWindows()) {
                    try {
                        // ignore a failure.
                        new LocalProc(new String[]{"rm","../lastSuccessful"},new String[0],listener.getLogger(),getProject().getBuildDir()).join();

                        int r = new LocalProc(new String[]{
                            "ln","-s","builds/"+getId()/*ugly*/,"../lastSuccessful"},
                            new String[0],listener.getLogger(),getProject().getBuildDir()).join();
                        if(r!=0)
                            listener.getLogger().println("ln failed: "+r);
                    } catch (IOException e) {
                        PrintStream log = listener.getLogger();
                        log.println("ln failed");
                        Util.displayIOException(e,listener);
                        e.printStackTrace( log );
                    }
                }

                return Result.SUCCESS;
            }

            public void post(BuildListener listener) {
                // run all of them even if one of them failed
                try {
                    for( Publisher bs : project.getPublishers().values() )
                        bs.perform(Build.this, launcher, listener);
                } catch (InterruptedException e) {
                    e.printStackTrace(listener.fatalError("aborted"));
                } catch (IOException e) {
                    e.printStackTrace(listener.fatalError("failed"));
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
        });
    }

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

        JDK jdk = project.getJDK();
        if(jdk !=null)
            jdk.buildEnvVars(env);
        project.getScm().buildEnvVars(env);

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
    /**
     * Stops this build if it's still going.
     *
     * If we use this/executor/stop URL, it causes 404 if the build is already killed,
     * as {@link #getExecutor()} returns null.
     */
    public synchronized void doStop( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        Executor e = getExecutor();
        if(e!=null)
            e.doStop(req,rsp);
        else
            // nothing is building
            rsp.forwardToPreviousPage(req);
    }
}
