package hudson.tasks.junit;

import hudson.Launcher;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.tasks.AntBasedPublisher;
import hudson.tasks.Publisher;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Generates HTML report from JUnit test result XML files.
 *
 * @author Kohsuke Kawaguchi
 */
public class JUnitResultArchiver extends AntBasedPublisher {

    /**
     * {@link FileSet} "includes" string, like "foo/bar/*.xml"
     */
    private final String testResults;

    public JUnitResultArchiver(String testResults) {
        this.testResults = testResults;
    }

    public boolean perform(Build build, Launcher launcher, BuildListener listener) {
        FileSet fs = new FileSet();
        Project p = new Project();
        fs.setProject(p);
        fs.setDir(build.getProject().getWorkspace().getLocal());
        fs.setIncludes(testResults);
        DirectoryScanner ds = fs.getDirectoryScanner(p);

        if(ds.getIncludedFiles().length==0) {
            listener.getLogger().println("No test report files were found. Configuration error?");
            // no test result. Most likely a configuration error or fatal problem
            build.setResult(Result.FAILURE);
        }

        TestResultAction action = new TestResultAction(build, ds, listener);
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
        return DESCRIPTOR;
    }

    public static final Descriptor<Publisher> DESCRIPTOR = new Descriptor<Publisher>(JUnitResultArchiver.class) {
        public String getDisplayName() {
            return "Publish JUnit test result report";
        }

        public Publisher newInstance(StaplerRequest req) {
            return new JUnitResultArchiver(req.getParameter("junitreport_includes"));
        }
    };
}
