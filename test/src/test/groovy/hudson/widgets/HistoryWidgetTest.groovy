package hudson.widgets

import org.jvnet.hudson.test.HudsonTestCase
import org.jvnet.hudson.test.Bug

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class HistoryWidgetTest extends HudsonTestCase {
    @Bug(15499)
    void testMoreLink() {
        def p = createFreeStyleProject();
        for (x in 1..3) {
            assertBuildStatusSuccess(p.scheduleBuild2(0))
        }

        def wc = createWebClient()
        wc.javaScriptEnabled = false
        wc.goTo("job/${p.name}/buildHistory/all");
    }
}
