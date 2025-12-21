package hudson.views;

import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ListView;
import hudson.model.Result;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class LastDurationColumnTest {

    @Test
    public void lastDurationShouldShowLastCompletedBuild(JenkinsRule j) throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("test-project");

        // 1. Run a successful build
        FreeStyleBuild s = j.buildAndAssertSuccess(p);
        String sDurationString = s.getDurationString();

        // 2. Run a failed build
        p.getBuildersList().add(new hudson.tasks.Shell("exit 1"));
        FreeStyleBuild f = j.buildAndAssertStatus(Result.FAILURE, p);
        String fDurationString = f.getDurationString();

        // 3. Verify the view shows the last completed build's duration (the failed one)
        ListView view = new ListView("lastDurationTestView", j.jenkins);
        view.getColumns().add(new LastDurationColumn());
        view.add(p);
        j.jenkins.addView(view);

        JenkinsRule.WebClient wc = j.createWebClient();
        String viewContent = wc.goTo("view/lastDurationTestView").asText();

        assertTrue(viewContent.contains(fDurationString), 
            "View should contain the failed build's duration (" + fDurationString + ")");
    }
}
