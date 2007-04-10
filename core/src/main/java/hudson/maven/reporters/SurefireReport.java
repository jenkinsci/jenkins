package hudson.maven.reporters;

import hudson.maven.MavenBuild;
import hudson.maven.AggregatableAction;
import hudson.maven.MavenAggregatedReport;
import hudson.maven.MavenModuleSetBuild;
import hudson.maven.MavenModule;
import hudson.model.BuildListener;
import hudson.model.Action;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;

import java.util.List;
import java.util.Map;

/**
 * {@link Action} that displays surefire test result.
 * @author Kohsuke Kawaguchi
 */
public class SurefireReport extends TestResultAction implements AggregatableAction {
    SurefireReport(MavenBuild build, TestResult result, BuildListener listener) {
        super(build, result, listener);
    }

    public MavenAggregatedReport createAggregatedAction(MavenModuleSetBuild build, Map<MavenModule, List<MavenBuild>> moduleBuilds) {
        return new SurefireAggregatedReport(build);
    }
}
