package hudson.model;

import hudson.Launcher;
import hudson.Proc.LocalProc;
import hudson.Util;
import hudson.maven.MavenBuild;
import static hudson.model.Hudson.isWindows;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Calendar;

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

        protected abstract Result doRun(BuildListener listener) throws Exception;
    }
}
