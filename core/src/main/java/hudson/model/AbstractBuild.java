package hudson.model;

import hudson.Launcher;
import hudson.Proc.LocalProc;
import hudson.Util;
import hudson.tasks.Fingerprinter.FingerprintAction;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.maven.MavenBuild;
import static hudson.model.Hudson.isWindows;
import hudson.model.listeners.SCMListener;
import hudson.model.Fingerprint.RangeSet;
import hudson.model.Fingerprint.BuildPtr;
import hudson.scm.CVSChangeLogParser;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import hudson.scm.SCM;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.xml.sax.SAXException;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Calendar;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

/**
 * Base implementation of {@link Run}s that build software.
 *
 * For now this is primarily the common part of {@link Build} and {@link MavenBuild}.
 *
 * @author Kohsuke Kawaguchi
 * @see AbstractProject
 */
public abstract class AbstractBuild<P extends AbstractProject<P,R>,R extends AbstractBuild<P,R>> extends Run<P,R> implements Runnable {

    /**
     * PluginName of the slave this project was built on.
     * Null or "" if built by the master. (null happens when we read old record that didn't have this information.)
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

    protected AbstractBuild(P job) throws IOException {
        super(job);
    }

    protected AbstractBuild(P job, Calendar timestamp) {
        super(job, timestamp);
    }

    protected AbstractBuild(P project, File buildDir) throws IOException {
        super(project, buildDir);
    }

    public final P getProject() {
        return getParent();
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

    protected abstract class AbstractRunner implements Runner {
        /**
         * Since configuration can be changed while a build is in progress,
         * stick to one launcher and use it.
         */
        protected Launcher launcher;

        /**
         * Returns the current {@link Node} on which we are buildling.
         */
        protected final Node getCurrentNode() {
            return Executor.currentExecutor().getOwner().getNode();
        }

        public Result run(BuildListener listener) throws Exception {
            Node node = Executor.currentExecutor().getOwner().getNode();
            assert builtOn==null;
            builtOn = node.getNodeName();

            launcher = node.createLauncher(listener);
            if(node instanceof Slave)
                listener.getLogger().println("Building remotely on "+node.getNodeName());

            if(checkout(listener))
                return Result.FAILURE;

            Result result = doRun(listener);
            if(result!=null)
                return result;  // abort here

            if(getResult()==null || getResult()==Result.SUCCESS)
                createLastSuccessfulLink(listener);

            return Result.SUCCESS;
        }

        private void createLastSuccessfulLink(BuildListener listener) {
            if(!isWindows()) {
                try {
                    // ignore a failure.
                    new LocalProc(new String[]{"rm","-f","../lastSuccessful"},new String[0],listener.getLogger(),getProject().getBuildDir()).join();

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
        }

        private boolean checkout(BuildListener listener) throws Exception {
            if(!project.checkout(AbstractBuild.this,launcher,listener,new File(getRootDir(),"changelog.xml")))
                return true;

            SCM scm = project.getScm();

            AbstractBuild.this.scm = scm.createChangeLogParser();
            AbstractBuild.this.changeSet = AbstractBuild.this.calcChangeSet();

            for (SCMListener l : Hudson.getInstance().getSCMListeners())
                l.onChangeLogParsed(AbstractBuild.this,listener,changeSet);
            return false;
        }

        /**
         * The portion of a build that is specific to a subclass of {@link AbstractBuild}
         * goes here.
         *
         * @return
         *      null to continue the build normally (that means the doRun method
         *      itself run successfully)
         *      Return a non-null value to abort the build right there with the specified result code.
         */
        protected abstract Result doRun(BuildListener listener) throws Exception;
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
            return ChangeLogSet.createEmpty(this);

        try {
            return scm.parse(this,changelogFile);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }
        return ChangeLogSet.createEmpty(this);
    }

    @Override
    public Map<String,String> getEnvVars() {
        Map<String,String> env = super.getEnvVars();

        JDK jdk = project.getJDK();
        if(jdk !=null)
            jdk.buildEnvVars(env);
        project.getScm().buildEnvVars(env);

        return env;
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

    /**
     * Invoked by {@link Executor} to performs a build.
     */
    public abstract void run();

//
//
// fingerprint related stuff
//
//

    @Override
    public String getWhyKeepLog() {
        // if any of the downstream project is configured with 'keep dependency component',
        // we need to keep this log
        for (Map.Entry<AbstractProject, RangeSet> e : getDownstreamBuilds().entrySet()) {
            AbstractProject<?,?> p = e.getKey();
            if(!p.isKeepDependencies())     continue;

            // is there any active build that depends on us?
            for (AbstractBuild build : p.getBuilds()) {
                if(e.getValue().includes(build.getNumber()))
                    return "kept because of "+build;
            }
        }

        return super.getWhyKeepLog();
    }

    /**
     * Gets the dependency relationship from this build (as the source)
     * and that project (as the sink.)
     *
     * @return
     *      range of build numbers that represent which downstream builds are using this build.
     *      The range will be empty if no build of that project matches this.
     */
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
     * Gets the downstream builds of this build, which are the builds of the
     * downstream projects that use artifacts of this build.
     *
     * @return
     *      For each project with fingerprinting enabled, returns the range
     *      of builds (which can be empty if no build uses the artifact from this build.)
     */
    public Map<AbstractProject,RangeSet> getDownstreamBuilds() {
        Map<AbstractProject,RangeSet> r = new HashMap<AbstractProject,RangeSet>();
        for (AbstractProject p : getParent().getDownstreamProjects()) {
            if(p.isFingerprintConfigured())
                r.put(p,getDownstreamRelationship(p));
        }
        return r;
    }
    
    /**
     * Gets the upstream builds of this build, which are the builds of the
     * upstream projects whose artifacts feed into this build.
     */
    public Map<AbstractProject,Integer> getUpstreamBuilds() {
        Map<AbstractProject,Integer> r = new HashMap<AbstractProject,Integer>();
        for (AbstractProject p : getParent().getUpstreamProjects()) {
            int n = getUpstreamRelationship(p);
            if(n>=0)
                r.put(p,n);
        }
        return r;
    }

    /**
     * Gets the changes in the dependency between the given build and this build.
     */
    public Map<AbstractProject,DependencyChange> getDependencyChanges(AbstractBuild from) {
        if(from==null)             return Collections.emptyMap(); // make it easy to call this from views
        FingerprintAction n = this.getAction(FingerprintAction.class);
        FingerprintAction o = from.getAction(FingerprintAction.class);
        if(n==null || o==null)     return Collections.emptyMap();

        Map<AbstractProject,Integer> ndep = n.getDependencies();
        Map<AbstractProject,Integer> odep = o.getDependencies();

        Map<AbstractProject,DependencyChange> r = new HashMap<AbstractProject,DependencyChange>();

        for (Map.Entry<AbstractProject,Integer> entry : odep.entrySet()) {
            AbstractProject p = entry.getKey();
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
        public final AbstractProject project;
        /**
         * Version of the dependency project used in the previous build.
         */
        public final int fromId;
        /**
         * {@link Build} object for {@link #fromId}. Can be null if the log is gone.
         */
        public final AbstractBuild from;
        /**
         * Version of the dependency project used in this build.
         */
        public final int toId;

        public final AbstractBuild to;

        public DependencyChange(AbstractProject<?,?> project, int fromId, int toId) {
            this.project = project;
            this.fromId = fromId;
            this.toId = toId;
            this.from = project.getBuildByNumber(fromId);
            this.to = project.getBuildByNumber(toId);
        }

        /**
         * Gets the {@link AbstractBuild} objects (fromId,toId].
         * <p>
         * This method returns all such available builds in the ascending order
         * of IDs, but due to log rotations, some builds may be already unavailable. 
         */
        public List<AbstractBuild> getBuilds() {
            List<AbstractBuild> r = new ArrayList<AbstractBuild>();

            AbstractBuild<?,?> b = (AbstractBuild)project.getNearestBuild(fromId);
            if(b!=null && b.getNumber()==fromId)
                b = b.getNextBuild(); // fromId exclusive

            while(b!=null && b.getNumber()<=toId) {
                r.add(b);
                b = b.getNextBuild();
            }

            return r;
        }
    }
    

//
//
// web methods
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
