package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import hudson.Functions;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.tasks.BatchFile;
import hudson.tasks.CommandInterpreter;
import hudson.tasks.Shell;
import java.util.concurrent.atomic.AtomicBoolean;
import org.htmlunit.html.HtmlPage;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class Security3188Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Issue("SECURITY-3188")
    @Test
    public void linkCannotAttributeEscape() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("p");
        p.getBuildersList().add(getScript("echo \"https://acme.com/search?q='onmouseover=alert(1);'Hello World\""));
        FreeStyleBuild build = j.buildAndAssertSuccess(p);

        AtomicBoolean alerts = new AtomicBoolean();
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wc.setAlertHandler((pr, s) -> alerts.set(true));
            final HtmlPage page = wc.goTo(build.getUrl() + "console");
            String content = page.getWebResponse().getContentAsString();
            assertThat(content, containsString("<a href='https://acme.com/search?q=&#39;onmouseover=alert(1);&#39;Hello'"));

            // Execute JavaScript code to simulate mouseover event
            String jsCode = "document.querySelector('pre.console-output a:nth-child(2)').dispatchEvent(new MouseEvent('mouseover'));";
            page.executeJavaScript(jsCode);

            Assert.assertFalse("Alert not expected", alerts.get());
        }
    }

    private static CommandInterpreter getScript(String script) {
        return Functions.isWindows()
                ? new BatchFile(script)
                : new Shell(script);
    }
}
