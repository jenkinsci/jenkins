package jenkins.bugs;

import hudson.model.FreeStyleProject;
import java.util.ArrayList;
import java.util.List;
import org.htmlunit.cssparser.parser.CSSErrorHandler;
import org.htmlunit.cssparser.parser.CSSException;
import org.htmlunit.cssparser.parser.CSSParseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.opentest4j.MultipleFailuresError;

@WithJenkins
class Jenkins14749Test {

    private JenkinsRule j;

    private final List<Throwable> errors = new ArrayList<>();

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @AfterEach
    void tearDown() {
        if (!errors.isEmpty()) {
            throw new MultipleFailuresError(null, errors);
        }
    }

    @Test
    void dashboard() throws Exception {
        JenkinsRule.WebClient webClient = createErrorReportingWebClient();
        webClient.goTo("");
    }

    @Test
    void project() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        JenkinsRule.WebClient webClient = createErrorReportingWebClient();
        webClient.getPage(p);
    }

    @Test
    void configureProject() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        JenkinsRule.WebClient webClient = createErrorReportingWebClient();
        webClient.getPage(p, "configure");
    }

    @Test
    void manage() throws Exception {
        JenkinsRule.WebClient webClient = createErrorReportingWebClient();
        webClient.goTo("manage");
    }

    @Test
    void system() throws Exception {
        JenkinsRule.WebClient webClient = createErrorReportingWebClient();
        webClient.goTo("manage/configure");
    }

    private JenkinsRule.WebClient createErrorReportingWebClient() {
        JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.setCssErrorHandler(new CSSErrorHandler() {
            @Override
            public void warning(final CSSParseException exception) throws CSSException {
                errors.add(exception);
            }

            @Override
            public void error(final CSSParseException exception) throws CSSException {
                errors.add(exception);
            }

            @Override
            public void fatalError(final CSSParseException exception) throws CSSException {
                errors.add(exception);
            }
        });
        return webClient;
    }
}
