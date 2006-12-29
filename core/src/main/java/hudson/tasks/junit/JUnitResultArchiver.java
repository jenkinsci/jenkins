package hudson.tasks.junit;

import hudson.Launcher;
import hudson.remoting.VirtualChannel;
import hudson.FilePath.FileCallable;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.tasks.Publisher;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

/**
 * Generates HTML report from JUnit test result XML files.
 *
 * @author Kohsuke Kawaguchi
 */
public class JUnitResultArchiver extends Publisher implements Serializable {

    /**
     * {@link FileSet} "includes" string, like "foo/bar/*.xml"
     */
    private final String testResults;

    public JUnitResultArchiver(String testResults) {
        this.testResults = testResults;
    }

    public boolean perform(Build build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        TestResult result;

        listener.getLogger().println("Recording test results");

        try {
            final long buildTime = build.getTimestamp().getTimeInMillis();

            result = build.getProject().getWorkspace().act(new FileCallable<TestResult>() {
                public TestResult invoke(File ws, VirtualChannel channel) throws IOException {
                    FileSet fs = new FileSet();
                    Project p = new Project();
                    fs.setProject(p);
                    fs.setDir(ws);
                    fs.setIncludes(testResults);
                    DirectoryScanner ds = fs.getDirectoryScanner(p);

                    if(ds.getIncludedFiles().length==0) {
                        // no test result. Most likely a configuration error or fatal problem
                        throw new AbortException("No test report files were found. Configuration error?");
                    }

                    return new TestResult(buildTime,ds);
                }
            });
        } catch (AbortException e) {
            listener.getLogger().println(e.getMessage());
            build.setResult(Result.FAILURE);
            return true;
        }


        TestResultAction action = new TestResultAction(build, result, listener);
        build.getActions().add(action);

        TestResult r = action.getResult();

        if(r.getPassCount()==0 && r.getFailCount()==0) {
            listener.getLogger().println("Test reports were found but none of them are new. Did tests run?");
            // no test result. Most likely a configuration error or fatal problem
            build.setResult(Result.FAILURE);
        }

        if(r.getFailCount()>0)
            build.setResult(Result.UNSTABLE);

        return true;
    }

    public String getTestResults() {
        return testResults;
    }


    public Descriptor<Publisher> getDescriptor() {
        return DescriptorImpl.DESCRIPTOR;
    }

    private static final long serialVersionUID = 1L;

    public static class DescriptorImpl extends Descriptor<Publisher> {
        public static final Descriptor<Publisher> DESCRIPTOR = new DescriptorImpl();

        public DescriptorImpl() {
            super(JUnitResultArchiver.class);
        }

        public String getDisplayName() {
            return "Publish JUnit test result report";
        }

        public Publisher newInstance(StaplerRequest req) {
            return new JUnitResultArchiver(req.getParameter("junitreport_includes"));
        }
    }
}
