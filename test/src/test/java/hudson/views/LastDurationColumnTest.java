package hudson.views;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import hudson.model.ListView;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@WithJenkins
class LastDurationColumnTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void lastDurationShouldShowLastCompletedBuild() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();

        // 1. Run a successful build
        FreeStyleBuild s = j.buildAndAssertSuccess(p);
        String sDurationString = s.getDurationString();

        // 2. Run a failed build (make it fail)
        p.getBuildersList().add(new hudson.tasks.Shell("exit 1"));
        FreeStyleBuild f = j.buildAndAssertStatus(Result.FAILURE, p);
        String fDurationString = f.getDurationString();

        JenkinsRule.WebClient wc = j.createWebClient();

        ListView view = new ListView("testView", j.jenkins);
        view.getColumns().add(new LastDurationColumn());
        view.add(p);
        j.jenkins.addView(view);

        String viewContent = wc.goTo("view/testView").asNormalizedText();

        assertThat("View should contain failed build's duration", viewContent, containsString(fDurationString));
    }
}
