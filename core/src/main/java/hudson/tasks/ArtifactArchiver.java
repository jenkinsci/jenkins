package hudson.tasks;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.util.FormFieldValidator;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Project;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;

/**
 * Copies the artifacts into an archive directory.
 *
 * @author Kohsuke Kawaguchi
 */
public class ArtifactArchiver extends Publisher {

    /**
     * Comma-separated list of files/directories to be archived.
     */
    private final String artifacts;

    /**
     * Possibly null 'excludes' pattern as in Ant.
     */
    private final String excludes;

    /**
     * Just keep the last successful artifact set, no more.
     */
    private final boolean latestOnly;

    public ArtifactArchiver(String artifacts, String excludes, boolean latestOnly) {
        this.artifacts = artifacts;
        this.excludes = excludes;
        this.latestOnly = latestOnly;
    }

    public String getArtifacts() {
        return artifacts;
    }

    public String getExcludes() {
        return excludes;
    }

    public boolean isLatestOnly() {
        return latestOnly;
    }

    public boolean perform(Build<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
        Project<?,?> p = build.getProject();

        File dir = build.getArtifactsDir();
        dir.mkdirs();

        try {
            p.getWorkspace().copyRecursiveTo(artifacts,excludes,new FilePath(dir));
        } catch (IOException e) {
            Util.displayIOException(e,listener);
            e.printStackTrace(listener.error("Failed to archive artifacts: "+artifacts));
            return true;
        }

        if(latestOnly) {
            Build<?,?> b = p.getLastSuccessfulBuild();
            if(b!=null) {
                while(true) {
                    b = b.getPreviousBuild();
                    if(b==null)     break;

                    // remove old artifacts
                    File ad = b.getArtifactsDir();
                    if(ad.exists()) {
                        listener.getLogger().println("Deleting old artifacts from "+b.getDisplayName());
                        try {
                            Util.deleteRecursive(ad);
                        } catch (IOException e) {
                            e.printStackTrace(listener.error(e.getMessage()));
                        }
                    }
                }
            }
        }

        return true;
    }

    public Descriptor<Publisher> getDescriptor() {
        return DESCRIPTOR;
    }


    public static final Descriptor<Publisher> DESCRIPTOR = new DescriptorImpl();

    public static class DescriptorImpl extends Descriptor<Publisher> {
        public DescriptorImpl() {
            super(ArtifactArchiver.class);
        }

        public String getDisplayName() {
            return "Archive the artifacts";
        }

        public String getHelpFile() {
            return "/help/project-config/archive-artifact.html";
        }

        /**
         * Performs on-the-fly validation on the file mask wildcard.
         */
        public void doCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            new FormFieldValidator.WorkspaceFileMask(req,rsp).process();
        }

        public Publisher newInstance(StaplerRequest req) {
            return new ArtifactArchiver(
                req.getParameter("artifacts").trim(),
                Util.fixEmpty(req.getParameter("artifacts_excludes").trim()),
                req.getParameter("artifacts_latest_only")!=null);
        }
    }
}
