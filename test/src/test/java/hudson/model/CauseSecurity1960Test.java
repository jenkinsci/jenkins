package hudson.model;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import org.xml.sax.SAXException;

import java.io.IOException;

// TODO merge into CauseTest after release
public class CauseSecurity1960Test {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("SECURITY-1960")
    @LocalData
    public void xssInRemoteCause() throws IOException, SAXException {
        final Item item = j.jenkins.getItemByFullName("fs");
        Assert.assertTrue(item instanceof FreeStyleProject);
        FreeStyleProject fs = (FreeStyleProject) item;
        final FreeStyleBuild build = fs.getBuildByNumber(1);

        final JenkinsRule.WebClient wc = j.createWebClient();
        final String content = wc.getPage(build).getWebResponse().getContentAsString();
        Assert.assertFalse(content.contains("Started by remote host <img"));
        Assert.assertTrue(content.contains("Started by remote host &lt;img"));
    }
}
