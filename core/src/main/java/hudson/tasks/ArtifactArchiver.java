package hudson.tasks;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Project;
import org.kohsuke.stapler.StaplerRequest;

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
     * Just keep the last successful artifact set, no more.
     */
    private final boolean latestOnly;

    public ArtifactArchiver(String artifacts, boolean latestOnly) {
        this.artifacts = artifacts;
        this.latestOnly = latestOnly;
    }

    public String getArtifacts() {
        return artifacts;
    }

    public boolean isLatestOnly() {
        return latestOnly;
    }

    public boolean perform(Build build, Launcher launcher, BuildListener listener) throws InterruptedException {
        Project p = build.getProject();

        File dir = build.getArtifactsDir();
        dir.mkdirs();

        try {
            p.getWorkspace().copyRecursiveTo(artifacts,new FilePath(dir));
        } catch (IOException e) {
            Util.displayIOException(e,listener);
            e.printStackTrace(listener.error("Failed to archive artifacts: "+artifacts));
            return true;
        }

        if(latestOnly) {
            Build b = p.getLastSuccessfulBuild();
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


    public static final Descriptor<Publisher> DESCRIPTOR = new Descriptor<Publisher>(ArtifactArchiver.class) {
        public String getDisplayName() {
            return "Archive the artifacts";
        }

        public String getHelpFile() {
            return "/help/project-config/archive-artifact.html";
        }

        public Publisher newInstance(StaplerRequest req) {
            return new ArtifactArchiver(
                req.getParameter("artifacts").trim(),
                req.getParameter("artifacts_latest_only")!=null);
        }
    };
}
