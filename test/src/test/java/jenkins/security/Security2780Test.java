package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;

import hudson.model.FreeStyleProject;
import java.util.concurrent.atomic.AtomicBoolean;
import org.htmlunit.ScriptResult;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class Security2780Test {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void buildButtonTooltipHasNoXss() throws Exception {
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.setDisplayName("<img src=x onerror=alert(1)>");
        JenkinsRule.WebClient wc = this.j.createWebClient();

        AtomicBoolean alertTriggered = new AtomicBoolean(false);
        wc.setAlertHandler((p, s) -> alertTriggered.set(true));
        HtmlPage page = wc.goTo("");
        page.executeJavaScript("document.querySelector('.jenkins-table a.jenkins-button')._tippy.show()");
        wc.waitForBackgroundJavaScript(2000L);
        ScriptResult result = page.executeJavaScript("document.querySelector('.tippy-content').innerHTML;");
        Object jsResult = result.getJavaScriptResult();
        assertThat(jsResult, instanceOf(String.class));
        String jsResultString = (String) jsResult;

        assertThat("No unsafe HTML expected in the tooltip", jsResultString, not(containsString("<img")));
        assertThat("Safe HTML expected in the tooltip", jsResultString, containsString("Schedule a Build for &lt;img"));
        assertFalse(alertTriggered.get(), "No alert expected");
    }
}
