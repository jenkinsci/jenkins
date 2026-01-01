package hudson.views;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ListView;
import hudson.model.Result;
import org.htmlunit.html.HtmlPage;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SleepBuilder;

public class LastDurationColumnTest {

    @org.junit.Rule
    public JenkinsRule j = new JenkinsRule();

    @org.junit.Test
    public void showsDurationOfLastCompletedBuildEvenIfFailed() throws Exception {

        FreeStyleProject p = j.createFreeStyleProject("test-job");

        p.getBuildersList().add(new SleepBuilder(700));
        FreeStyleBuild oldSuccess = j.buildAndAssertSuccess(p);

        p.getBuildersList().clear();
        p.getBuildersList().add(new FailureBuilder());
        FreeStyleBuild newFailure = p.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, newFailure);

        String oldDuration = oldSuccess.getDurationString();
        String newDuration = newFailure.getDurationString();

        ListView view = new ListView("TestView", j.jenkins);
        view.add(p);
        view.getColumns().clear();
        view.getColumns().add(new StatusColumn());
        view.getColumns().add(new JobColumn());
        view.getColumns().add(new LastDurationColumn());
        j.jenkins.addView(view);

                HtmlPage page = j.createWebClient().getPage(view);
        String pageText = page.asNormalizedText();

        assertThat("Page should show the duration of the latest build (failure)", pageText, containsString(newDuration));

        if (!oldDuration.equals(newDuration)) {
            assertThat("Page should NOT show the duration of the old successful build", pageText, not(containsString(oldDuration)));
        }
    }
}
