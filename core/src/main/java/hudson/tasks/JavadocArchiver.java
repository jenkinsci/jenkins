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
    private static File getJavadocDir(Project project) {
        return new File(project.getRootDir(),"javadoc");
    }

    public boolean perform(Build build, Launcher launcher, BuildListener listener) throws InterruptedException {
        listener.getLogger().println("Publishing Javadoc");

        FilePath javadoc = build.getParent().getWorkspace().child(javadocDir);
        FilePath target = new FilePath(getJavadocDir(build.getParent()));

        try {
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
        private final Project project;

        public JavadocAction(Project project) {
            this.project = project;
        }

        public String getUrlName() {
            return "javadoc";
        }

        public String getDisplayName() {
            return "Javadoc";
        }

        public String getIconFileName() {
            return "help.gif";
        }

        public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException {
            new DirectoryBrowserSupport(this).serveFile(req, rsp, new FilePath(getJavadocDir(project)), "help.gif", false);
        }
    }
}
