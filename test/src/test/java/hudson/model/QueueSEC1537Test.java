package hudson.model;

import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.DomNodeList;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.slaves.NodeProvisionerRule;
import org.apache.commons.lang.StringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

//TODO merge into QueueTest after security patch
public class QueueSEC1537Test {

    @Rule
    public JenkinsRule r = new NodeProvisionerRule(-1, 0, 10);

    @Test
    @Issue("SECURITY-1537")
    public void regularTooltipDisplayedCorrectly() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();

        String expectedLabel = "\"expected label\"";
        p.setAssignedLabel(Label.get(expectedLabel));

        p.scheduleBuild2(0);

        String tooltip = buildAndExtractTooltipAttribute();
        assertThat(tooltip, containsString(expectedLabel.substring(1, expectedLabel.length() - 1)));
    }

    @Test
    @Issue("SECURITY-1537")
    public void preventXssInCauseOfBlocking() throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.setAssignedLabel(Label.get("\"<img/src='x' onerror=alert(123)>xss\""));

        p.scheduleBuild2(0);

        String tooltip = buildAndExtractTooltipAttribute();
        assertThat(tooltip, not(containsString("<img")));
        assertThat(tooltip, containsString("&lt;"));
    }

    private String buildAndExtractTooltipAttribute() throws Exception {
        JenkinsRule.WebClient wc = r.createWebClient();

        HtmlPage page = wc.goTo("");

        DomElement buildQueue = page.getElementById("buildQueue");
        DomNodeList<HtmlElement> anchors = buildQueue.getElementsByTagName("a");
        HtmlAnchor anchorWithTooltip = (HtmlAnchor) anchors.stream()
                .filter(a -> StringUtils.isNotEmpty(a.getAttribute("tooltip")))
                .findFirst().orElseThrow(IllegalStateException::new);

        String tooltip = anchorWithTooltip.getAttribute("tooltip");
        return tooltip;
    }
}
