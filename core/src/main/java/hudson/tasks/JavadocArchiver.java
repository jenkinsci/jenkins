package hudson.tasks;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.maven.MavenModuleSet;
import hudson.maven.AbstractMavenProject;
import hudson.model.*;
import hudson.util.FormFieldValidator;

import org.kohsuke.stapler.DataBoundConstructor;
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
    
    @DataBoundConstructor
    public JavadocArchiver(String javadoc_dir) {
        this.javadocDir = javadoc_dir;
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

    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
        listener.getLogger().println(Messages.JavadocArchiver_Publishing());

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
            e.printStackTrace(listener.fatalError(Messages.JavadocArchiver_UnableToCopy(javadoc,target)));
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


    public static final Descriptor<Publisher> DESCRIPTOR = new DescriptorImpl();

    public static class JavadocAction implements ProminentProjectAction {
        private final AbstractItem project;

        public JavadocAction(AbstractItem project) {
            this.project = project;
        }

        public String getUrlName() {
            return "javadoc";
        }

        public String getDisplayName() {
            if(new File(getJavadocDir(project),"help-doc.html").exists())
                return Messages.JavadocArchiver_DisplayName_Javadoc();
            else
                return Messages.JavadocArchiver_DisplayName_Generic();
        }

        public String getIconFileName() {
            if(getJavadocDir(project).exists())
                return "help.gif";
            else
                // hide it since we don't have javadoc yet.
                return null;
        }

        public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException {
            new DirectoryBrowserSupport(this,project.getDisplayName()+" javadoc")
                .serveFile(req, rsp, new FilePath(getJavadocDir(project)), "help.gif", false);
        }
    }

    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        private DescriptorImpl() {
            super(JavadocArchiver.class);
        }

        public String getDisplayName() {
            return Messages.JavadocArchiver_DisplayName();
        }

        /**
         * Performs on-the-fly validation on the file mask wildcard.
         */
        public void doCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            new FormFieldValidator.WorkspaceDirectory(req,rsp).process();
        }

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            // for Maven, javadoc archiving kicks in automatically
            return !AbstractMavenProject.class.isAssignableFrom(jobType);
        }
    }
}
