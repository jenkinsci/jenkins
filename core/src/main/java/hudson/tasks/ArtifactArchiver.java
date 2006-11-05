package hudson.tasks;

import hudson.Launcher;
import hudson.Util;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Project;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.taskdefs.Delete;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;

/**
 * Copies the artifacts into an archive directory.
 *
 * @author Kohsuke Kawaguchi
 */
public class ArtifactArchiver extends AntBasedPublisher {

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

    public boolean prebuild(Build build, BuildListener listener) {
        listener.getLogger().println("Removing artifacts from the previous build");

        File dir = build.getArtifactsDir();
        if(!dir.exists())   return true;

        Delete delTask = new Delete();
        delTask.setProject(new org.apache.tools.ant.Project());
        delTask.setDir(dir);
        delTask.setIncludes(artifacts);

        execTask(delTask,listener);

        return true;
    }

    public boolean perform(Build build, Launcher launcher, BuildListener listener) {
        Project p = build.getProject();

        Copy copyTask = new Copy();
        copyTask.setProject(new org.apache.tools.ant.Project());
        File dir = build.getArtifactsDir();
        dir.mkdirs();
        copyTask.setTodir(dir);
        FileSet src = new FileSet();
        src.setDir(p.getWorkspace().getLocal());
        src.setIncludes(artifacts);
        copyTask.addFileset(src);

        execTask(copyTask, listener);

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
