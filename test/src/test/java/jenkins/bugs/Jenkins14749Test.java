package jenkins.bugs;

import hudson.model.FreeStyleProject;
import org.htmlunit.cssparser.parser.CSSErrorHandler;
import org.htmlunit.cssparser.parser.CSSException;
import org.htmlunit.cssparser.parser.CSSParseException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.jvnet.hudson.test.JenkinsRule;

public class Jenkins14749Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public ErrorCollector errors = new ErrorCollector();

    @Test
    public void dashboard() throws Exception {
        JenkinsRule.WebClient webClient = createErrorReportingWebClient();
        webClient.goTo("");
    }

    @Test
    public void project() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        JenkinsRule.WebClient webClient = createErrorReportingWebClient();
        webClient.getPage(p);
    }

    @Test
    public void configureProject() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        JenkinsRule.WebClient webClient = createErrorReportingWebClient();
        webClient.getPage(p, "configure");
    }

    @Test
    public void manage() throws Exception {
        JenkinsRule.WebClient webClient = createErrorReportingWebClient();
        webClient.goTo("manage");
    }

    @Test
    public void system() throws Exception {
        JenkinsRule.WebClient webClient = createErrorReportingWebClient();
        webClient.goTo("manage/configure");
    }

    private JenkinsRule.WebClient createErrorReportingWebClient() {
        JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.setCssErrorHandler(new CSSErrorHandler() {
            @Override
            public void warning(final CSSParseException exception) throws CSSException {
                if (!ignore(exception)) {
                    errors.addError(exception);
                }
            }

            @Override
            public void error(final CSSParseException exception) throws CSSException {
                if (!ignore(exception)) {
                    errors.addError(exception);
                }
            }

            @Override
            public void fatalError(final CSSParseException exception) throws CSSException {
                if (!ignore(exception)) {
                    errors.addError(exception);
                }
            }

            private boolean ignore(final CSSParseException exception) {
                // Keep in sync with HudsonTestCase/JenkinsRule
                return exception.getURI().contains("/yui/");
            }
        });
        return webClient;
    }
}
