package hudson.model;

import com.gargoylesoftware.htmlunit.html.DomNode;
import com.gargoylesoftware.htmlunit.html.DomNodeList;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.Util;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;

//TODO merge back to AbstractProjectTest when security release is done. 
// Creating a different test class is used to ease the security merge process.
public class AbstractProjectSEC1781Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void ensureWhenNonExistingLabelsProposalsAreMade() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        FreeStyleProject p = j.createFreeStyleProject();

        String label = "whatever";
        HtmlPage htmlPage = this.requestCheckAssignedLabelString(p, label);
        String responseContent = htmlPage.getWebResponse().getContentAsString();
        /* Sample:
         *
         * <div class=warning><img src='/jenkins/static/03a3de4a/images/none.gif' height=16 width=1>There’s no agent/cloud that
         *     matches this assignment. Did you mean ‘master’ instead of ‘whatever’?
         * </div>
         */
        assertThat(responseContent, allOf(
                containsString("warning"),
                // as there is only master that is currently used, it's de facto the nearest to whatever
                containsString("master"),
                containsString("whatever")
        ));
    }

    @Test
    public void ensureLegitLabelsAreRetrievedCorrectly() throws Exception {
        j.jenkins.setCrumbIssuer(null);
        j.jenkins.setLabelString("existing");
        FreeStyleProject p = j.createFreeStyleProject();

        String label = "existing";
        HtmlPage htmlPage = this.requestCheckAssignedLabelString(p, label);
        String responseContent = htmlPage.getWebResponse().getContentAsString();
        /* Sample:
         *
         * <div class=ok><img src='/jenkins/static/32591acf/images/none.gif' height=16 width=1>
         *   <a href="http://localhost:5595/jenkins/label/existing/">Label existing</a>
         *   is serviced by 1 node. Permissions or other restrictions provided by plugins may prevent 
         *   this job from running on those nodes.
         * </div>
         */
        assertThat(responseContent, allOf(
                containsString("ok"),
                containsString("label/existing/\">")
        ));
    }

    @Test
    @Issue("SECURITY-1781")
    public void dangerousLabelsAreEscaped() throws Exception {
        j.jenkins.setCrumbIssuer(null);

        // unescaped: "\"><img src=x onerror=alert(123)>"
        String label = "\"\\\"><img src=x onerror=alert(123)>\"";
        j.jenkins.setLabelString(label);
        FreeStyleProject p = j.createFreeStyleProject();

        HtmlPage htmlPage = this.requestCheckAssignedLabelString(p, label);
        String responseContent = htmlPage.getWebResponse().getContentAsString();
        /* Sample (before correction)
         *
         * <div class=ok><img src='/jenkins/static/793045c3/images/none.gif' height=16 width=1>
         *   <a href="http://localhost:5718/jenkins/label/"><img src=x onerror=alert(123)>/">Label &quot;&gt;&lt;img src=x 
         *      onerror=alert(123)&gt;</a>
         *   is serviced by 1 node. Permissions or other restrictions provided by plugins may prevent
         *   this job from running on those nodes.
         * </div>
         */
        /* Sample (after correction)
         * <div class=ok><img src='/jenkins/static/e16858e2/images/none.gif' height=16 width=1>
         *   <a href="http://localhost:6151/jenkins/label/%22%3E%3Cimg%20src=x%20onerror=alert(123)%3E/">
         *     Label &quot;&gt;&lt;img src=x onerror=alert(123)&gt;</a> 
         *   is serviced by 1 node. 
         *   Permissions or other restrictions provided by plugins may prevent this job from running on those nodes.
         * </div>
         */
        DomNodeList<DomNode> domNodes = htmlPage.getDocumentElement().querySelectorAll("*");
        assertThat(domNodes, hasSize(5));
        assertEquals("head", domNodes.get(0).getNodeName());
        assertEquals("body", domNodes.get(1).getNodeName());
        assertEquals("div", domNodes.get(2).getNodeName());
        assertEquals("img", domNodes.get(3).getNodeName());
        assertEquals("a", domNodes.get(4).getNodeName());

        // only: "><img src=x onerror=alert(123)>
        // the first double quote was escaped during creation (with the backslash)
        String unquotedLabel = Label.parseExpression(label).getName();
        HtmlAnchor anchor = (HtmlAnchor) domNodes.get(4);
        assertThat(anchor.getHrefAttribute(), containsString(Util.rawEncode(unquotedLabel)));

        assertThat(responseContent, containsString("ok"));
    }

    private HtmlPage requestCheckAssignedLabelString(FreeStyleProject p, String label) throws Exception {
        return j.createWebClient().goTo(p.getUrl() + p.getDescriptor().getDescriptorUrl() + "/checkAssignedLabelString?value=" + Util.rawEncode(label));
    }
}
