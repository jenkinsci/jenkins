package hudson.model;

import static org.junit.Assert.assertEquals;

import java.util.logging.Level;
import jenkins.model.lazy.LazyBuildMixIn;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

/**
 * Unit test for {@link Job}.
 */
@SuppressWarnings("rawtypes")
public class SimpleJobTest {

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule().record(LazyBuildMixIn.class, Level.FINE);

    @Test
    public void testGetEstimatedDuration() throws Exception {
        var project = r.createFreeStyleProject("testGetEstimatedDuration");

        var b1 = r.buildAndAssertSuccess(project);
        b1.duration = 200;
        assertEquals(200, project.getEstimatedDuration());

        var b2 = r.buildAndAssertSuccess(project);
        b2.duration = 150;
        assertEquals(175, project.getEstimatedDuration());

        var b3 = r.buildAndAssertSuccess(project);
        b3.duration = 400;
        assertEquals(250, project.getEstimatedDuration());
    }

    @Test
    public void testGetEstimatedDurationWithOneRun() throws Exception {
        var project = r.createFreeStyleProject("testGetEstimatedDurationWithOneRun");

        var b1 = r.buildAndAssertSuccess(project);
        b1.duration = 420;
        assertEquals(420, project.getEstimatedDuration());
    }

    @Test
    public void testGetEstimatedDurationWithFailedRun() throws Exception {
        var project = r.createFreeStyleProject("testGetEstimatedDurationWithFailedRun");

        var b1 = r.buildAndAssertSuccess(project);
        b1.result = Result.FAILURE;
        b1.duration = 420;
        assertEquals(420, project.getEstimatedDuration());
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
        b1.duration = 100;
        assertEquals(100, project.getEstimatedDuration());

        var b2 = r.buildAndAssertSuccess(project);
        b2.duration = 200;
        assertEquals(150, project.getEstimatedDuration());

        var b3 = r.buildAndAssertSuccess(project);
        b3.duration = 300;
        assertEquals(200, project.getEstimatedDuration());

        var b4 = r.buildAndAssertSuccess(project);
        b4.result = Result.FAILURE;
        b4.duration = 500;
        assertEquals(200, project.getEstimatedDuration());

        var b5 = r.buildAndAssertSuccess(project);
        b5.result = Result.FAILURE;
        b5.duration = 500;
        assertEquals(200, project.getEstimatedDuration());

        var b6 = r.buildAndAssertSuccess(project);
        b6.result = Result.FAILURE;
        b6.duration = 500;
        assertEquals(200, project.getEstimatedDuration());
    }

    @Test
    public void testGetEstimatedDurationIfNoSuccessfulBuildTakeDurationOfFailedBuild() throws Exception {
        var project = r.createFreeStyleProject("testGetEstimatedDurationIfNoSuccessfulBuildTakeDurationOfFailedBuild");

        var b1 = r.buildAndAssertSuccess(project);
        b1.result = Result.FAILURE;
        b1.duration = 500;
        assertEquals(500, project.getEstimatedDuration());
    }

}
