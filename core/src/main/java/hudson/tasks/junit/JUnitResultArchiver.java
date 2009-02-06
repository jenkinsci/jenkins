/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Martin Eigenbrodt
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
package hudson.tasks.junit;

import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.Util;
import hudson.maven.AbstractMavenProject;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.model.*;
import hudson.remoting.VirtualChannel;
import hudson.tasks.Publisher;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.test.TestResultAggregator;
import hudson.tasks.test.TestResultProjectAction;
import hudson.util.FormFieldValidator;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;

/**
 * Generates HTML report from JUnit test result XML files.
 *
 * @author Kohsuke Kawaguchi
 */
public class JUnitResultArchiver extends Publisher implements Serializable, MatrixAggregatable {

    /**
     * {@link FileSet} "includes" string, like "foo/bar/*.xml"
     */
    private final String testResults;
    
    @DataBoundConstructor
    public JUnitResultArchiver(String junitreport_includes) {
        this.testResults = junitreport_includes;
    }

    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        listener.getLogger().println(Messages.JUnitResultArchiver_Recording());
        TestResultAction action;

        try {
            final long buildTime = build.getTimestamp().getTimeInMillis();
            final long nowMaster = System.currentTimeMillis();

            TestResult result = build.getProject().getWorkspace().act(new FileCallable<TestResult>() {
                public TestResult invoke(File ws, VirtualChannel channel) throws IOException {
                    final long nowSlave = System.currentTimeMillis();

                    FileSet fs = Util.createFileSet(ws,testResults);
                    DirectoryScanner ds = fs.getDirectoryScanner();

                    String[] files = ds.getIncludedFiles();
                    if(files.length==0) {
                        // no test result. Most likely a configuration error or fatal problem
                        throw new AbortException(Messages.JUnitResultArchiver_NoTestReportFound());
                    }

                    return new TestResult(buildTime+(nowSlave-nowMaster), ds);
                }
            });

            action = new TestResultAction(build, result, listener);
            if(result.getPassCount()==0 && result.getFailCount()==0)
                throw new AbortException(Messages.JUnitResultArchiver_ResultIsEmpty());
        } catch (IOException e) {
            e.printStackTrace(listener.error("Failed to archive JUnit reports"));
            build.setResult(Result.FAILURE);
            return true;
        } catch (AbortException e) {
            if(build.getResult()==Result.FAILURE)
                // most likely a build failed before it gets to the test phase.
                // don't report confusing error message.
                return true;

            listener.getLogger().println(e.getMessage());
            build.setResult(Result.FAILURE);
            return true;
        }


        build.getActions().add(action);

        if(action.getResult().getFailCount()>0)
            build.setResult(Result.UNSTABLE);

        return true;
    }

    public String getTestResults() {
        return testResults;
    }

    public Action getProjectAction(hudson.model.Project project) {
        return new TestResultProjectAction(project);
    }


    public MatrixAggregator createAggregator(MatrixBuild build, Launcher launcher, BuildListener listener) {
        return new TestResultAggregator(build,launcher,listener);
    }

    public Descriptor<Publisher> getDescriptor() {
        return DescriptorImpl.DESCRIPTOR;
    }

    private static final long serialVersionUID = 1L;

    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public static final Descriptor<Publisher> DESCRIPTOR = new DescriptorImpl();

        public String getDisplayName() {
            return Messages.JUnitResultArchiver_DisplayName();
        }

        public String getHelpFile() {
            return "/help/tasks/junit/report.html";
        }

        /**
         * Performs on-the-fly validation on the file mask wildcard.
         */
        public void doCheck(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
            new FormFieldValidator.WorkspaceFileMask(req,rsp).process();
        }

        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            // for Maven we have SurefireArchiver that automatically kicks in.
            return !AbstractMavenProject.class.isAssignableFrom(jobType);
        }
    }
}
