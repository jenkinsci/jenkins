/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Martin Eigenbrodt, Peter Hayes
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.tasks;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.Extension;
import hudson.model.*;
import hudson.util.FormValidation;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.AncestorInPath;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;

/**
 * Saves Javadoc for the project and publish them. 
 *
 * @author Kohsuke Kawaguchi
 */
public class JavadocArchiver extends Recorder {
    /**
     * Path to the Javadoc directory in the workspace.
     */
    private final String javadocDir;
    /**
     * If true, retain javadoc for all the successful builds.
     */
    private final boolean keepAll;
    
    @DataBoundConstructor
    public JavadocArchiver(String javadoc_dir, boolean keep_all) {
        this.javadocDir = javadoc_dir;
        this.keepAll = keep_all;
    }

    public String getJavadocDir() {
        return javadocDir;
    }

    public boolean isKeepAll() {
        return keepAll;
    }

    /**
     * Gets the directory where the Javadoc is stored for the given project.
     */
    private static File getJavadocDir(AbstractItem project) {
        return new File(project.getRootDir(),"javadoc");
    }

    /**
     * Gets the directory where the Javadoc is stored for the given build.
     */
    private static File getJavadocDir(Run run) {
        return new File(run.getRootDir(),"javadoc");
    }

    public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
        listener.getLogger().println(Messages.JavadocArchiver_Publishing());

        FilePath javadoc = build.getParent().getWorkspace().child(javadocDir);
        FilePath target = new FilePath(keepAll ? getJavadocDir(build) : getJavadocDir(build.getProject()));

        try {
            if (javadoc.copyRecursiveTo("**/*",target)==0) {
                if(build.getResult().isBetterOrEqualTo(Result.UNSTABLE)) {
                    // If the build failed, don't complain that there was no javadoc.
                    // The build probably didn't even get to the point where it produces javadoc.
                    listener.error(Messages.JavadocArchiver_NoMatchFound(javadoc,javadoc.validateAntFileMask("**/*")));
                }
                build.setResult(Result.FAILURE);
                return true;
            }
        } catch (IOException e) {
            Util.displayIOException(e,listener);
            e.printStackTrace(listener.fatalError(Messages.JavadocArchiver_UnableToCopy(javadoc,target)));
            build.setResult(Result.FAILURE);
             return true;
        }
        
        // add build action, if javadoc is recorded for each build
        if(keepAll)
            build.addAction(new JavadocBuildAction(build));
        
        return true;
    }

    public Action getProjectAction(AbstractProject project) {
        return new JavadocAction(project);
    }

    protected static abstract class BaseJavadocAction implements Action {
        public String getUrlName() {
            return "javadoc";
        }

        public String getDisplayName() {
            if (new File(dir(), "help-doc.html").exists())
                return Messages.JavadocArchiver_DisplayName_Javadoc();
            else
                return Messages.JavadocArchiver_DisplayName_Generic();
        }

        public String getIconFileName() {
            if(dir().exists())
                return "help.gif";
            else
                // hide it since we don't have javadoc yet.
                return null;
        }

        /**
         * Serves javadoc.
         */
        public void doDynamic(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            new DirectoryBrowserSupport(this, new FilePath(dir()), getTitle(), "help.gif", false).generateResponse(req,rsp,this);
        }

        protected abstract String getTitle();

        protected abstract File dir();
    }

    public static class JavadocAction extends BaseJavadocAction implements ProminentProjectAction {
        private final AbstractItem project;

        public JavadocAction(AbstractItem project) {
            this.project = project;
        }

        protected File dir() {
            // Would like to change AbstractItem to AbstractProject, but is
            // that a backwards compatible change?
            if (project instanceof AbstractProject) {
                AbstractProject abstractProject = (AbstractProject) project;

                Run run = abstractProject.getLastSuccessfulBuild();
                if (run != null) {
                    File javadocDir = getJavadocDir(run);

                    if (javadocDir.exists())
                        return javadocDir;
                }
            }

            return getJavadocDir(project);
        }

        protected String getTitle() {
            return project.getDisplayName()+" javadoc";
        }
    }
    
    public static class JavadocBuildAction extends BaseJavadocAction {
    	private final AbstractBuild<?,?> build;
    	
    	public JavadocBuildAction(AbstractBuild<?,?> build) {
    	    this.build = build;
    	}

        protected String getTitle() {
            return build.getDisplayName()+" javadoc";
        }

        protected File dir() {
            return getJavadocDir(build);
        }
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public String getDisplayName() {
            return Messages.JavadocArchiver_DisplayName();
        }

        /**
         * Performs on-the-fly validation on the file mask wildcard.
         */
        public FormValidation doCheck(@AncestorInPath AbstractProject project, @QueryParameter String value) throws IOException, ServletException {
            FilePath ws = project.getWorkspace();
            return ws != null ? ws.validateRelativeDirectory(value) : FormValidation.ok();
        }

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
