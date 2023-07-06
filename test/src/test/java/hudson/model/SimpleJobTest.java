package hudson.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Unit test for {@link Job}.
 */
@SuppressWarnings("rawtypes")
public class SimpleJobTest {

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Test
    public void testGetEstimatedDuration() throws Exception {
        var project = r.createFreeStyleProject("testGetEstimatedDuration");

        var b1 = r.buildAndAssertSuccess(project);
        b1.duration = 20;

        var b2 = r.buildAndAssertSuccess(project);
        b2.duration = 15;

        var b3 = r.buildAndAssertSuccess(project);
        b3.duration = 42;

        // without assuming to know too much about the internal calculation
        // we can only assume that the result is between the maximum and the minimum
        assertTrue("Expected < 42, but was " + project.getEstimatedDuration(), project.getEstimatedDuration() < 42);
        assertTrue("Expected > 15, but was " + project.getEstimatedDuration(), project.getEstimatedDuration() > 15);
    }

    @Test
    public void testGetEstimatedDurationWithOneRun() throws Exception {
        var project = r.createFreeStyleProject("testGetEstimatedDurationWithOneRun");

        var b1 = r.buildAndAssertSuccess(project);
        b1.duration = 42;

        assertEquals(42, project.getEstimatedDuration());
    }

    @Test
    public void testGetEstimatedDurationWithFailedRun() throws Exception {
        var project = r.createFreeStyleProject("testGetEstimatedDurationWithFailedRun");

        var b1 = r.buildAndAssertSuccess(project);
        b1.result = Result.FAILURE;
        b1.duration = 42;

        assertEquals(42, project.getEstimatedDuration());
    }

    @Test
    public void testGetEstimatedDurationWithNoRuns() throws Exception {
        var project = r.createFreeStyleProject("testGetEstimatedDurationWithNoRuns");

        assertEquals(-1, project.getEstimatedDuration());
    }

    @Test
    public void testGetEstimatedDurationIfPrevious3BuildsFailed() throws Exception {
        var project = r.createFreeStyleProject("testGetEstimatedDurationIfPrevious3BuildsFailed");

        var b1 = r.buildAndAssertSuccess(project);
        b1.result = Result.UNSTABLE;
        b1.duration = 1;

        var b2 = r.buildAndAssertSuccess(project);
        b2.duration = 1;

        var b3 = r.buildAndAssertSuccess(project);
        b3.duration = 1;

        var b4 = r.buildAndAssertSuccess(project);
        b4.result = Result.FAILURE;
        b4.duration = 50;

        var b5 = r.buildAndAssertSuccess(project);
        b5.result = Result.FAILURE;
        b5.duration = 50;

        var b6 = r.buildAndAssertSuccess(project);
        b6.result = Result.FAILURE;
        b6.duration = 50;

        // failed builds must not be used, if there are successfulBuilds available.
        assertEquals(1, project.getEstimatedDuration());
    }

    @Test
    public void testGetEstimatedDurationIfNoSuccessfulBuildTakeDurationOfFailedBuild() throws Exception {
        var project = r.createFreeStyleProject("testGetEstimatedDurationIfNoSuccessfulBuildTakeDurationOfFailedBuild");

        var b1 = r.buildAndAssertSuccess(project);
        b1.result = Result.FAILURE;
        b1.duration = 50;

        assertEquals(50, project.getEstimatedDuration());
    }

}
