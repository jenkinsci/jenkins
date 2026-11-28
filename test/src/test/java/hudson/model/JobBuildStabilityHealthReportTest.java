package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class JobBuildStabilityHealthReportTest {

    private FreeStyleProject failedThenSuccessful(JenkinsRule j) throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new FailureBuilder());
        j.buildAndAssertStatus(Result.FAILURE, p);
        p.getBuildersList().clear();
        j.buildAndAssertSuccess(p);
        return p;
    }

    @Test
    void defaultConsidersLastFiveBuilds(JenkinsRule j) throws Exception {
        // 1 failure out of 2 builds
        assertEquals(50, failedThenSuccessful(j).getBuildHealth().getScore());
    }

    @Test
    void buildCountOfOneConsidersOnlyLatestBuild(JenkinsRule j) throws Exception {
        int original = Job.BUILD_STABILITY_HEALTH_REPORT_BUILD_COUNT;
        try {
            Job.BUILD_STABILITY_HEALTH_REPORT_BUILD_COUNT = 1;
            assertEquals(100, failedThenSuccessful(j).getBuildHealth().getScore());
        } finally {
            Job.BUILD_STABILITY_HEALTH_REPORT_BUILD_COUNT = original;
        }
    }
}