package hudson.widgets

import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.Issue
import org.jvnet.hudson.test.JenkinsRule

/**
 * @author Kohsuke Kawaguchi
 */
class HistoryWidgetTest {

    @Rule
    public JenkinsRule j = new JenkinsRule()

    @Test
    @Issue("JENKINS-15499")
    void moreLink() {
        def p = j.createFreeStyleProject()
        for (x in 1..3) {
            j.assertBuildStatusSuccess(p.scheduleBuild2(0))
        }

        def wc = j.createWebClient()
        wc.javaScriptEnabled = false
        wc.goTo("job/${p.name}/buildHistory/all")
    }
}
