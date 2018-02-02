package hudson.widgets;

import hudson.model.FreeStyleProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class HistoryWidgetTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-15499")
    public void moreLink() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        for (int x = 0; x < 3; x++) {
            j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        }

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.setJavaScriptEnabled(false);
        wc.goTo("job/" + p.getName() + "/buildHistory/all");
    }

}
