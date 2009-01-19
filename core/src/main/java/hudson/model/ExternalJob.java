package hudson.model;

import hudson.model.AbstractProject;
import hudson.model.RunMap.Constructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Job that runs outside Hudson whose result is submitted to Hudson
 * (either via web interface, or simply by placing files on the file system,
 * for compatibility.)
 *
 * @author Kohsuke Kawaguchi
 */
public class ExternalJob extends ViewJob<ExternalJob,ExternalRun> implements TopLevelItem {
    public ExternalJob(String name) {
        super(Hudson.getInstance(),name);
    }

    @Override
    public Hudson getParent() {
        return (Hudson)super.getParent();
    }

    @Override
    protected void reload() {
        this.runs.load(this,new Constructor<ExternalRun>() {
            public ExternalRun create(File dir) throws IOException {
                return new ExternalRun(ExternalJob.this,dir);
            }
        });
    }


    /**
     * Creates a new build of this project for immediate execution.
     *
     * Needs to be synchronized so that two {@link #newBuild()} invocations serialize each other.
     */
    public ExternalRun newBuild() throws IOException {
        ExternalRun run = new ExternalRun(this);
        runs.put(run);
        return run;
    }

    /**
     * Used to check if this is an external job and ready to accept a build result.
     */
    public void doAcceptBuildResult( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        rsp.setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * Used to post the build result from a remote machine.
     */
    public void doPostBuildResult( StaplerRequest req, StaplerResponse rsp ) throws IOException, ServletException {
        checkPermission(AbstractProject.BUILD);
        ExternalRun run = newBuild();
        run.acceptRemoteSubmission(req.getReader());
        rsp.setStatus(HttpServletResponse.SC_OK);
    }


    private static final Logger logger = Logger.getLogger(ExternalJob.class.getName());

    public TopLevelItemDescriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public static final TopLevelItemDescriptor DESCRIPTOR = new DescriptorImpl();

    @Override
    public String getPronoun() {
        return Messages.ExternalJob_Pronoun();
    }

    public static final class DescriptorImpl extends TopLevelItemDescriptor {
        private DescriptorImpl() {
            super(ExternalJob.class);
        }

        public String getDisplayName() {
            return Messages.ExternalJob_DisplayName();
        }

        public ExternalJob newInstance(String name) {
            return new ExternalJob(name);
        }
    }
}
