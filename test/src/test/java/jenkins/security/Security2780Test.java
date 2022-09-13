package jenkins.security;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.gargoylesoftware.htmlunit.ScriptResult;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.FreeStyleProject;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class Security2780Test {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void buildButtonTooltipHasNoXss() throws Exception {
        FreeStyleProject project = this.j.createFreeStyleProject();
        project.setDisplayName("<img src=x onerror=alert(1)>");
        JenkinsRule.WebClient wc = this.j.createWebClient();

        AtomicBoolean alertTriggered = new AtomicBoolean(false);
        wc.setAlertHandler((p, s) -> alertTriggered.set(true));
        HtmlPage page = wc.goTo("");
        page.executeJavaScript("document.querySelector('a.jenkins-table__button').dispatchEvent(new Event('mouseover'));");
        wc.waitForBackgroundJavaScript(2000L);
        ScriptResult result = page.executeJavaScript("document.querySelector('#tt').innerHTML;");
        Object jsResult = result.getJavaScriptResult();
        assertThat(jsResult, instanceOf(String.class));
        String jsResultString = (String) jsResult;

        assertThat("No unsafe HTML expected in the tooltip", jsResultString, not(containsString("<img")));
        assertThat("Safe HTML expected in the tooltip", jsResultString, containsString("Schedule a Build for &lt;img"));
        Assert.assertFalse("No alert expected", alertTriggered.get());
    }
}
