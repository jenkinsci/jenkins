package hudson.model;

import hudson.Launcher;
import hudson.Proc.LocalProc;
import hudson.Util;
import hudson.maven.MavenBuild;
import static hudson.model.Hudson.isWindows;
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

        public final Result run(BuildListener listener) throws Exception {
            Node node = Executor.currentExecutor().getOwner().getNode();
            assert builtOn==null;
            builtOn = node.getNodeName();

            launcher = node.createLauncher(listener);
            if(node instanceof Slave)
                listener.getLogger().println("Building remotely on "+node.getNodeName());

            if(!project.checkout(AbstractBuild.this,launcher,listener,new File(getRootDir(),"changelog.xml")))
                return Result.FAILURE;

            SCM scm = project.getScm();

            AbstractBuild.this.scm = scm.createChangeLogParser();
            AbstractBuild.this.changeSet = AbstractBuild.this.calcChangeSet();

            Result result = doRun(listener);
            if(result!=null)
                return result;  // abort here

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

    @Override
    public Map<String,String> getEnvVars() {
        Map<String,String> env = super.getEnvVars();

        JDK jdk = project.getJDK();
        if(jdk !=null)
            jdk.buildEnvVars(env);
        project.getScm().buildEnvVars(env);

        return env;
    }

    /**
     * Invoked by {@link Executor} to performs a build.
     */
    public abstract void run();

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
