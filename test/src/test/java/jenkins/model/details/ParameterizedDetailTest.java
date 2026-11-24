package jenkins.model.details;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import org.htmlunit.ScriptResult;
import org.htmlunit.WebClientUtil;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ParameterizedDetailTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * The dialog is rendered on demand, in a request of its own, so the build is not among the ancestors of that
     * request. {@code value.jelly} still has to be able to tell which build the parameters belong to.
     */
    @Test
    void valueJellyKnowsTheBuildWhenTheDialogIsRenderedOnDemand() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject("p");
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("sample", "")));
        FreeStyleBuild b = j.assertBuildStatusSuccess(
                p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(new BuildAwareValue("sample"))));

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            HtmlPage page = wc.getPage(b, "changes");
            page.executeJavaScript(
                    "renderOnDemand(document.querySelector('.render-on-demand.dialog-parameters-hash'))");
            WebClientUtil.waitForJSExec(wc);

            DomElement dialog = page.querySelector("template[data-dialog-hash='parameters']");
            assertNotNull(dialog, "the Parameters dialog was not rendered");
            ScriptResult content = page.executeJavaScript(
                    "document.querySelector(\"template[data-dialog-hash='parameters']\").content.textContent");
            assertThat(content.getJavaScriptResult().toString(), containsString("sample was built by p #1"));
        }
    }

    /**
     * A parameter value whose {@code value.jelly} prints the build it is shown for.
     */
    public static final class BuildAwareValue extends ParameterValue {

        BuildAwareValue(String name) {
            super(name);
        }
    }
}
