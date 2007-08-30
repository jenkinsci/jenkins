package hudson.maven.reporters;

import hudson.maven.MavenBuild;
import hudson.maven.MavenBuildProxy;
import hudson.maven.MavenBuildProxy.BuildCallable;
import hudson.maven.MavenBuilder;
import hudson.maven.MavenModule;
import hudson.maven.MavenReporter;
import hudson.maven.MavenReporterDescriptor;
import hudson.maven.MojoInfo;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.junit.TestResult;
import hudson.tasks.test.TestResultProjectAction;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Date;

/**
 * Records the surefire test result.
 * @author Kohsuke Kawaguchi
 */
public class SurefireArchiver extends MavenReporter {
    private transient Date started;

    public boolean preExecute(MavenBuildProxy build, MavenProject pom, MojoInfo mojo, BuildListener listener) throws InterruptedException, IOException {
        if (isSurefireTest(mojo)) {
            started = new Date();

            // tell surefire:test to keep going even if there was a failure,
            // so that we can record this as yellow.
            if(mojo.configuration.getChild("testFailureIgnore")==null) {
                XmlPlexusConfiguration configuration = new XmlPlexusConfiguration("testFailureIgnore");
                configuration.setValue("true");
                mojo.configuration.addChild(configuration);
            }
        }
        return true;
    }

    public boolean postExecute(MavenBuildProxy build, MavenProject pom, MojoInfo mojo, final BuildListener listener, Throwable error) throws InterruptedException, IOException {
        if (!isSurefireTest(mojo)) return true;

        listener.getLogger().println("[HUDSON] Recording test results");

        File reportsDir;
        try {
            reportsDir = mojo.getConfigurationValue("reportsDirectory", File.class);
        } catch (ComponentConfigurationException e) {
            e.printStackTrace(listener.fatalError("Unable to obtain the reportsDirectory from surefire:test mojo"));
            build.setResult(Result.FAILURE);
            return true;
        }

        if(reportsDir.exists()) {
            // surefire:test just skips itself when the current project is not a java project

            FileSet fs = new FileSet();
            Project p = new Project();
            fs.setProject(p);
            fs.setDir(reportsDir);
            fs.setIncludes("*.xml");
            DirectoryScanner ds = fs.getDirectoryScanner(p);

            if(ds.getIncludedFiles().length==0)
                // no test in this module
                return true;

            final TestResult tr = new TestResult(started.getTime() - 1000/*error margin*/, ds);

            int failCount = build.execute(new BuildCallable<Integer, IOException>() {
                public Integer call(MavenBuild build) throws IOException, InterruptedException {
                    build.getActions().add(new SurefireReport(build, tr, listener));
                    if(tr.getFailCount()>0)
                        build.setResult(Result.UNSTABLE);
                    build.registerAsProjectAction(SurefireArchiver.this);
                    return tr.getFailCount();
                }
            });

            // if surefire plugin is going to kill maven because of a test failure,
            // intercept that (or otherwise build will be marked as failure)
            if(failCount>0 && error instanceof MojoFailureException) {
                MavenBuilder.markAsSuccess = true;
            }
        }

        return true;
    }


    public Action getProjectAction(MavenModule module) {
        return new TestResultProjectAction(module);
    }

    private boolean isSurefireTest(MojoInfo mojo) {
        return mojo.pluginName.matches("org.apache.maven.plugins", "maven-surefire-plugin") && mojo.getGoal().equals("test");
    }

    public DescriptorImpl getDescriptor() {
        return DescriptorImpl.DESCRIPTOR;
    }

    public static final class DescriptorImpl extends MavenReporterDescriptor {
        public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

        private DescriptorImpl() {
            super(SurefireArchiver.class);
        }

        public String getDisplayName() {
            return "Publish surefire reports";
        }

        public SurefireArchiver newAutoInstance(MavenModule module) {
            return new SurefireArchiver();
        }
    }

    private static final long serialVersionUID = 1L;
}
