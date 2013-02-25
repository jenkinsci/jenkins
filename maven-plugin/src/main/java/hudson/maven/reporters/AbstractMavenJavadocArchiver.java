/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * Olivier Lamy
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
package hudson.maven.reporters;

import hudson.FilePath;
import hudson.Util;
import hudson.maven.*;
import hudson.model.AbstractItem;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.JavadocArchiver.JavadocAction;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * Records the javadoc and archives it.
 * 
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractMavenJavadocArchiver extends MavenReporter {


    private boolean aggregated = false;

    private FilePath target;


    /**
     * return true if this mojo is a javadoc one sources or test sources
     * @param mojo
     * @return
     */
    public abstract boolean checkIsJavadocMojo(MojoInfo mojo);

    public abstract String getArchiveTargetPath();

    public boolean postExecute(MavenBuildProxy build, MavenProject pom, MojoInfo mojo, BuildListener listener, Throwable error) throws InterruptedException, IOException {
        if (!checkIsJavadocMojo(mojo)) return true;

        File destDir;
        try {
            aggregated = mojo.getConfigurationValue("aggregate",Boolean.class, Boolean.FALSE) || mojo.getGoal().equals("aggregate")
                            || mojo.getGoal().equals("test-aggregate");
            if(aggregated && !pom.isExecutionRoot())
                return true;    // in the aggregated mode, the generation will only happen for the root module

            destDir = mojo.getConfigurationValue("reportOutputDirectory", File.class);
            if(destDir==null)
                destDir = mojo.getConfigurationValue("outputDirectory", File.class);
        } catch (ComponentConfigurationException e) {
            e.printStackTrace(listener.fatalError(Messages.MavenJavadocArchiver_NoDestDir()));
            build.setResult(Result.FAILURE);
            return true;
        }

        if(destDir != null && destDir.exists()) {
            // javadoc:javadoc just skips itself when the current project is not a java project
            if(aggregated) {
                // store at MavenModuleSet level. 
                listener.getLogger().println("[JENKINS] Archiving aggregated javadoc");
                target = build.getModuleSetRootDir();
            } else {

                listener.getLogger().println("[JENKINS] Archiving  javadoc");
                target = build.getProjectRootDir();
            }

            target = target.child(getArchiveTargetPath());

            try {
                new FilePath(destDir).copyRecursiveTo("**/*", target);
            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace(listener.fatalError(Messages.MavenJavadocArchiver_FailedToCopy(destDir,target)));
                build.setResult(Result.FAILURE);
            }

            if(aggregated)
                build.registerAsAggregatedProjectAction(this);
            else
                build.registerAsProjectAction(this);

            // JENKINS-9202 if project without any module
            if  (pom.getModules() != null &&  pom.getModules().isEmpty() && pom.isExecutionRoot() ) {
                build.registerAsAggregatedProjectAction(this);
            }

        }

        return true;
    }

    @Override
    public boolean reportGenerated(MavenBuildProxy build, MavenProject pom, MavenReportInfo report, BuildListener listener) throws InterruptedException, IOException {
        return postExecute(build,pom,report,listener,null);
    }

    public abstract Collection<? extends Action> getProjectActions(MavenModule project);

    public abstract Action getAggregatedProjectAction(MavenModuleSet project);

    public FilePath getTarget() {
        return target;
    }

    protected static class MavenJavadocAction extends JavadocAction {
        private final AbstractItem abstractItem;
        private final FilePath target;
        private final String title;
        private final String urlName;
        private final String displayName;

        public MavenJavadocAction(AbstractItem project,FilePath target, String title,String urlName,String displayName) {
            super(project);
            this.abstractItem = project;
            this.target = target;
            this.title = title;
            this.urlName = urlName;
            this.displayName = displayName;
        }

        public String getDisplayName() {
            File dir = dir();
            if (dir != null && new File(dir, "help-doc.html").exists())
                return this.displayName;
            else
                return hudson.tasks.Messages.JavadocArchiver_DisplayName_Generic();
        }


        @Override
        protected String getTitle() {
            return abstractItem.getDisplayName()+ " "+title;
        }

        @Override
        public String getUrlName() {
            return this.urlName;
        }

        @Override
        protected File dir() {
            return target == null ? null : new File(target.getRemote());
        }

    }


    private static final long serialVersionUID = 1L;
}
