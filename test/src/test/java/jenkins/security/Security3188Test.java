package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;

import hudson.Functions;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.tasks.BatchFile;
import hudson.tasks.CommandInterpreter;
import hudson.tasks.Shell;
import java.util.concurrent.atomic.AtomicBoolean;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class Security3188Test {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Issue("SECURITY-3188")
    @Test
    void linkCannotAttributeEscape() throws Exception {
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

            assertFalse(alerts.get(), "Alert not expected");
        }
    }

    private static CommandInterpreter getScript(String script) {
        return Functions.isWindows()
                ? new BatchFile(script)
                : new Shell(script);
    }
}
