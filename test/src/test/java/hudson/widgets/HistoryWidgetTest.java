package hudson.widgets;

import static org.junit.Assert.assertTrue;

import com.gargoylesoftware.htmlunit.html.DomNode;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.FreeStyleProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class HistoryWidgetTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-15499")
    public void moreLink() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        for (int x = 0; x < 3; x++) {
            j.buildAndAssertSuccess(p);
        }

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.setJavaScriptEnabled(false);
        wc.goTo("job/" + p.getName() + "/buildHistory/all");
    }

    @Test
    public void displayFilterInput() throws Exception {
        FreeStyleProject p = j.createFreeStyleProject();
        JenkinsRule.WebClient wc = j.createWebClient();

        { // Filter input shouldn't display when there's no build
            HtmlPage page = wc.goTo("job/" + p.getName());
            DomNode searchInputContainer = page.querySelector(".jenkins-search");
            assertTrue(searchInputContainer.getAttributes().getNamedItem("style").getNodeValue().contains("display: none"));
        }

        j.buildAndAssertSuccess(p);  // Add a build

        { // Filter input should display when there's a build
            HtmlPage page = wc.goTo("job/" + p.getName());
            DomNode searchInputContainer = page.querySelector(".jenkins-search");
            assertTrue(searchInputContainer.getAttributes().getNamedItem("style").getNodeValue().contains("display: block"));
        }
    }
}
