/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
import hudson.Extension;
import hudson.maven.MavenBuildProxy;
import hudson.maven.MavenModule;
import hudson.maven.MavenReporter;
import hudson.maven.MavenReporterDescriptor;
import hudson.maven.MojoInfo;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenReportInfo;
import hudson.model.*;
import hudson.tasks.JavadocArchiver.JavadocAction;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Records the javadoc and archives it.
 * 
 * @author Kohsuke Kawaguchi
 */
public class MavenJavadocArchiver extends MavenReporter {


    private boolean aggregated = false;

    private boolean testJavadoc = false;

    private FilePath target;

    private FilePath targetTestJavadoc;

    public boolean postExecute(MavenBuildProxy build, MavenProject pom, MojoInfo mojo, BuildListener listener, Throwable error) throws InterruptedException, IOException {
        if(!mojo.is("org.apache.maven.plugins","maven-javadoc-plugin","javadoc")
        && !mojo.is("org.apache.maven.plugins","maven-javadoc-plugin","aggregate")
        && !mojo.is("org.apache.maven.plugins","maven-javadoc-plugin","test-javadoc")
        && !mojo.is("org.apache.maven.plugins","maven-javadoc-plugin","test-aggregate"))
            return true;

        File destDir;
        try {
            aggregated = mojo.getConfigurationValue("aggregate",Boolean.class) || mojo.getGoal().equals("aggregate");
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

        if(destDir.exists()) {
            testJavadoc = "test-javadoc".equals(mojo.getGoal());
            // javadoc:javadoc just skips itself when the current project is not a java project 
            ;
            if(aggregated) {
                // store at MavenModuleSet level. 
                listener.getLogger().println("[JENKINS] Archiving aggregated javadoc");
                target = build.getModuleSetRootDir();
            } else {

                listener.getLogger().println("[JENKINS] Archiving "
                        + (!testJavadoc? "javadoc" : "test-javadoc"));
                target = build.getProjectRootDir();
            }

            target = target.child("javadoc");
            if (testJavadoc) targetTestJavadoc = target.child("test-javadoc");

            try {
                new FilePath(destDir).copyRecursiveTo("**/*",testJavadoc ? targetTestJavadoc : target);
            } catch (IOException e) {
                Util.displayIOException(e,listener);
                e.printStackTrace(listener.fatalError(Messages.MavenJavadocArchiver_FailedToCopy(destDir,target)));
                build.setResult(Result.FAILURE);
            }

            if(aggregated)
                build.registerAsAggregatedProjectAction(this);
            else
                build.registerAsProjectAction(this);

        }

        return true;
    }

    @Override
    public boolean reportGenerated(MavenBuildProxy build, MavenProject pom, MavenReportInfo report, BuildListener listener) throws InterruptedException, IOException {
        return postExecute(build,pom,report,listener,null);
    }

    public Collection<? extends Action> getProjectActions(MavenModule project) {
        // TODO test javadoc too
        List actions = new ArrayList();
        actions.add((new MavenJavadocAction(project,this.testJavadoc,this.target,this.targetTestJavadoc)));
        // adding action for test javadoc link display
        if (testJavadoc) actions.add((new MavenJavadocAction(project,this.testJavadoc,this.target,this.targetTestJavadoc)));
        return actions;
    }

    public Action getAggregatedProjectAction(MavenModuleSet project) {
        // TODO javadoc test too
        return new MavenJavadocAction(project,this.testJavadoc,this.target,this.targetTestJavadoc);
    }

    public static class MavenJavadocAction extends JavadocAction {
        private final AbstractItem abstractItem;
        private final boolean testJavadoc;
        private final FilePath target;
        private final FilePath targetTestJavadoc;

        public MavenJavadocAction(AbstractItem project, boolean testJavadoc,FilePath target, FilePath targetTestJavadoc) {
            super(project);
            this.abstractItem = project;
            this.testJavadoc = testJavadoc;
            this.target = target;
            this.targetTestJavadoc = targetTestJavadoc;
        }


        protected String getTitle() {
            return abstractItem.getDisplayName()+ (!testJavadoc ? " javadoc": "test javadoc");
        }

        public String getUrlName() {
            return !testJavadoc ? " javadoc": "test javadoc";
        }

        protected File dir() {
            if (testJavadoc) {
                return targetTestJavadoc == null ? null : new File(target.getRemote());
            }
            return target == null ? null : new File(target.getRemote());
        }

    }

    @Extension
    public static final class DescriptorImpl extends MavenReporterDescriptor {
        public String getDisplayName() {
            return Messages.MavenJavadocArchiver_DisplayName();
        }

        public MavenJavadocArchiver newAutoInstance(MavenModule module) {
            return new MavenJavadocArchiver();
        }
    }

    private static final long serialVersionUID = 1L;
}
