package jenkins.model;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.Run;
import java.nio.file.Files;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.JenkinsRule;

public class PeepholePermalinkTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Basic operation of the permalink generation.
     */
    @Test
    public void basics() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        FreeStyleBuild b1 = j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        String lsb = "lastSuccessfulBuild";
        String lfb = "lastFailedBuild";

        assertStorage(lsb, p, b1);

        // now another build that fails
        p.getBuildersList().add(new FailureBuilder());
        FreeStyleBuild b2 = p.scheduleBuild2(0).get();

        assertStorage(lsb, p, b1);
        assertStorage(lfb, p, b2);

        // one more build and this time it succeeds
        p.getBuildersList().clear();
        FreeStyleBuild b3 = j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        assertStorage(lsb, p, b3);
        assertStorage(lfb, p, b2);

        // delete b3 and links should update properly
        b3.delete();
        assertStorage(lsb, p, b1);
        assertStorage(lfb, p, b2);

        b1.delete();
        assertStorage(lsb, p, null);
        assertStorage(lfb, p, b2);

        b2.delete();
        assertStorage(lsb, p, null);
        assertStorage(lfb, p, null);
    }

    private void assertStorage(String id, Job<?, ?> job, Run<?, ?> build) throws Exception {
        assertThat(Files.readAllLines(PeepholePermalink.storageFor(job.getBuildDir()).toPath()),
            hasItem(id + " " + (build == null ? -1 : build.getNumber())));
    }

}
