package hudson.views;

import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ListView;
import hudson.model.Result;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class LastDurationColumnTest {

    @Test
    public void lastDurationShouldShowLastCompletedBuild(JenkinsRule j) throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("test-project");

        // 1. Run a successful build
        FreeStyleBuild s = j.buildAndAssertSuccess(p);
        String sDurationString = s.getDurationString();

        // 2. Run a failed build using a platform-independent TestBuilder
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                    throws InterruptedException, IOException {
                return false; // This fails the build
            }
        });
        FreeStyleBuild f = j.buildAndAssertStatus(Result.FAILURE, p);
        String fDurationString = f.getDurationString();

        // 3. Verify the view shows the last completed build's duration (the failed one)
        ListView view = new ListView("lastDurationTestView", j.jenkins);
        view.getColumns().add(new LastDurationColumn());
        view.add(p);
        j.jenkins.addView(view);

        JenkinsRule.WebClient wc = j.createWebClient();
        String viewContent = wc.goTo("view/lastDurationTestView").asNormalizedText();

        assertTrue(viewContent.contains(fDurationString),
                "View should contain the failed build's duration (" + fDurationString + ")");
    }
}
