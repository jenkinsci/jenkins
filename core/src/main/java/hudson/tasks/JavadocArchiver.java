package hudson.tasks;

import hudson.Launcher;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.DirectoryHolder;
import hudson.model.Project;
import hudson.model.ProminentProjectAction;
import org.apache.tools.ant.taskdefs.Copy;
import org.apache.tools.ant.types.FileSet;
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
public class JavadocArchiver extends AntBasedPublisher {
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

    public boolean perform(Build build, Launcher launcher, BuildListener listener) {
        // TODO: run tar or something for better remote copy
        File javadoc = new File(build.getParent().getWorkspace().getLocal(), javadocDir);
        if(!javadoc.exists()) {
            listener.error("The specified Javadoc directory doesn't exist: "+javadoc);
            return false;
        }
        if(!javadoc.isDirectory()) {
            listener.error("The specified Javadoc directory isn't a directory: "+javadoc);
            return false;
        }

        listener.getLogger().println("Publishing Javadoc");

        File target = getJavadocDir(build.getParent());
        target.mkdirs();

        Copy copyTask = new Copy();
        copyTask.setProject(new org.apache.tools.ant.Project());
        copyTask.setTodir(target);
        FileSet src = new FileSet();
        src.setDir(javadoc);
        copyTask.addFileset(src);

        execTask(copyTask, listener);

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

    public static final class JavadocAction extends DirectoryHolder implements ProminentProjectAction {
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

        public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            serveFile(req, rsp, getJavadocDir(project), "help.gif", false);
        }
    }
}
