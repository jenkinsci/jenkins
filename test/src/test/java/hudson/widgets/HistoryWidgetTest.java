package hudson.widgets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.FreeStyleProject;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class HistoryWidgetTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("JENKINS-15499")
    void moreLink() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        for (int x = 0; x < 3; x++) {
            j.buildAndAssertSuccess(p);
        }

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.setJavaScriptEnabled(false);
        wc.goTo("job/" + p.getName() + "/buildHistory/all");
    }

    @Test
    void displayFilterInput() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        JenkinsRule.WebClient wc = j.createWebClient();

        { // Filter input shouldn't display when there's no build
            HtmlPage page = wc.goTo("job/" + p.getName());
            DomNode searchInputContainer = page.querySelector("#jenkins-builds .jenkins-search");
            assertTrue(searchInputContainer.getAttributes().getNamedItem("class").getNodeValue().contains("jenkins-hidden"));
        }

        j.buildAndAssertSuccess(p);  // Add a build

        { // Filter input should display when there's a build
            HtmlPage page = wc.goTo("job/" + p.getName());
            DomNode searchInputContainer = page.querySelector("#jenkins-builds .jenkins-search");
            assertFalse(searchInputContainer.getAttributes().getNamedItem("class").getNodeValue().contains("jenkins-hidden"));
        }
    }
}
