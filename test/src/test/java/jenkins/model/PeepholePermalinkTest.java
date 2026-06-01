package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.Run;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class PeepholePermalinkTest {

    private final LogRecorder logging = new LogRecorder().record(PeepholePermalink.class, Level.FINE);

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Basic operation of the permalink generation.
     */
    @Issue("JENKINS-56809")
    @Test
    void basics() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        FreeStyleBuild b1 = j.buildAndAssertSuccess(p);

        String lsb = "lastSuccessfulBuild";
        String lfb = "lastFailedBuild";
        String lcb = "lastCompletedBuild";

        assertStorage(lsb, p, b1);
        /* TODO fix utility to also accept case that the permalink is not mentioned at all:
        assertStorage(lfb, p, null);
        */
        assertStorage(lcb, p, b1);

        // now another build that fails
        p.getBuildersList().add(new FailureBuilder());
        FreeStyleBuild b2 = p.scheduleBuild2(0).get();

        assertStorage(lsb, p, b1);
        assertStorage(lfb, p, b2);
        assertStorage(lcb, p, b2);

        // one more build and this time it succeeds
        p.getBuildersList().clear();
        FreeStyleBuild b3 = j.buildAndAssertSuccess(p);

        assertStorage(lsb, p, b3);
        assertStorage(lfb, p, b2);
        assertStorage(lcb, p, b3);

        assertEquals(b3, p.getLastSuccessfulBuild());
        assertEquals(b2, p.getLastFailedBuild());
        assertEquals(b3, p.getLastCompletedBuild());

        // delete b3 and links should update properly
        b3.delete();
        assertStorage(lsb, p, b1);
        assertStorage(lfb, p, b2);
        assertStorage(lcb, p, b2);

        b1.delete();
        assertStorage(lsb, p, null);
        assertStorage(lfb, p, b2);
        assertStorage(lcb, p, b2);

        b2.delete();
        assertStorage(lsb, p, null);
        assertStorage(lfb, p, null);
        assertStorage(lcb, p, null);
    }

    private void assertStorage(String id, Job<?, ?> job, Run<?, ?> build) throws Exception {
        assertThat(Files.readAllLines(PeepholePermalink.DefaultCache.storageFor(job.getBuildDir()).toPath(), StandardCharsets.UTF_8),
            hasItem(id + " " + (build == null ? -1 : build.getNumber())));
    }

}
