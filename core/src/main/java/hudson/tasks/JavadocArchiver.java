package hudson.tasks;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.Project;
import hudson.model.ProminentProjectAction;
import hudson.model.Result;
import hudson.model.Actionable;
import hudson.model.AbstractItem;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;

/**
 * Saves Javadoc for the project and publish them. 
 *
 * @author Kohsuke Kawaguchi
 */
public class JavadocArchiver extends Publisher {
    /**
     * Path to the Javadoc directory in the workspace.
     */
    private final String javadocDir;

    public JavadocArchiver(String javadocDir) {
        this.javadocDir = javadocDir;
    }

    public String getJavadocDir() {
        return javadocDir;
    }

    /**
     * Gets the directory where the Javadoc is stored for the given project.
     */
    private static File getJavadocDir(AbstractItem project) {
        return new File(project.getRootDir(),"javadoc");
    }

    public boolean perform(Build build, Launcher launcher, BuildListener listener) throws InterruptedException {
        listener.getLogger().println("Publishing Javadoc");

        FilePath javadoc = build.getParent().getWorkspace().child(javadocDir);
        FilePath target = new FilePath(getJavadocDir(build.getParent()));

        try {
            // if the build has failed, then there's not much point in reporting an error
            // saying javadoc directory doesn't exist. We want the user to focus on the real error,
            // which is the build failure.
            if(build.getResult().isWorseOrEqualTo(Result.FAILURE) && !javadoc.exists())
                return true;

            javadoc.copyRecursiveTo("**/*",target);
        } catch (IOException e) {
            Util.displayIOException(e,listener);
            e.printStackTrace(listener.fatalError("Unable to copy Javadoc from "+javadoc+" to "+target));
            build.setResult(Result.FAILURE);
        }

        return true;
    }

    public Action getProjectAction(Project project) {
        return new JavadocAction(project);
    }

    public Descriptor<Publisher> getDescriptor() {
        return DESCRIPTOR;
    }


    public static final Descriptor<Publisher> DESCRIPTOR = new Descriptor<Publisher>(JavadocArchiver.class) {
        public String getDisplayName() {
            return "Publish Javadoc";
        }

        public Publisher newInstance(StaplerRequest req) {
            return new JavadocArchiver(req.getParameter("javadoc_dir"));
        }
    };

    public static final class JavadocAction extends Actionable implements ProminentProjectAction {
        private final AbstractItem project;

        public JavadocAction(AbstractItem project) {
            this.project = project;
        }

        public String getUrlName() {
            return "javadoc";
        }

        public String getDisplayName() {
            if(new File(getJavadocDir(project),"help-doc.html").exists())
                return "Javadoc";
            else
                return "Document";
        }

        public String getIconFileName() {
            if(getJavadocDir(project).exists())
                return "help.gif";
            else
                // hide it since we don't have javadoc yet.
                return null;
        }

        public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException {
            new DirectoryBrowserSupport(this).serveFile(req, rsp, new FilePath(getJavadocDir(project)), "help.gif", false);
        }
    }
}
