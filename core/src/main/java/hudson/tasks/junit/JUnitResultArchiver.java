package hudson.tasks.junit;

import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.Util;
import hudson.matrix.MatrixAggregatable;
import hudson.matrix.MatrixAggregator;
import hudson.matrix.MatrixBuild;
import hudson.model.Action;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.remoting.VirtualChannel;
import hudson.tasks.Publisher;
import hudson.tasks.test.TestResultAggregator;
import hudson.tasks.test.TestResultProjectAction;
import hudson.util.FormFieldValidator;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
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

    public JUnitResultArchiver(String testResults) {
        this.testResults = testResults;
    }

    private static final class RecordResult implements Serializable {
        final TestResult tr;
        final String afile;
        final long lastModified;

        RecordResult(TestResult tr, String afile, long lastModified) {
            this.tr = tr;
            this.afile = afile;
            this.lastModified = lastModified;
        }

        private static final long serialVersionUID = 1L;
    }

    public boolean perform(Build build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        RecordResult result;
        listener.getLogger().println("Recording test results");

        try {
            final long buildTime = build.getTimestamp().getTimeInMillis();

            result = build.getProject().getWorkspace().act(new FileCallable<RecordResult>() {
                public RecordResult invoke(File ws, VirtualChannel channel) throws IOException {
                    FileSet fs = new FileSet();
                    Project p = new Project();
                    fs.setProject(p);
                    fs.setDir(ws);
                    fs.setIncludes(testResults);
                    DirectoryScanner ds = fs.getDirectoryScanner(p);

                    String[] files = ds.getIncludedFiles();
                    if(files.length==0) {
                        // no test result. Most likely a configuration error or fatal problem
                        throw new AbortException("No test report files were found. Configuration error?");
                    }

                    File oneFile = new File(ds.getBasedir(),files[0]);

                    return new RecordResult(new TestResult(buildTime,ds),files[0],oneFile.lastModified());
                }
            });

        } catch (AbortException e) {
            listener.getLogger().println(e.getMessage());
            build.setResult(Result.FAILURE);
            return true;
        }


        TestResultAction action = new TestResultAction(build, result.tr, listener);
        TestResult r = action.getResult();

        if(r.getPassCount()==0 && r.getFailCount()==0) {
            listener.getLogger().println("Test reports were found but none of them are new. Did tests run?");
            listener.getLogger().printf("For example, %s is %s old\n",result.afile, Util.getTimeSpanString(build.getTimestamp().getTimeInMillis()-result.lastModified));
            // no test result. Most likely a configuration error or fatal problem
            build.setResult(Result.FAILURE);
        } else {
            build.getActions().add(action);
        }

        if(r.getFailCount()>0)
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

    public static class DescriptorImpl extends Descriptor<Publisher> {
        public static final Descriptor<Publisher> DESCRIPTOR = new DescriptorImpl();

        public DescriptorImpl() {
            super(JUnitResultArchiver.class);
        }

        public String getDisplayName() {
            return "Publish JUnit test result report";
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

        public Publisher newInstance(StaplerRequest req) {
            return new JUnitResultArchiver(req.getParameter("junitreport_includes"));
        }
    }
}
