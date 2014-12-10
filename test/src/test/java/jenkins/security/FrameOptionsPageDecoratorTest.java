package jenkins.security;

import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.apache.commons.httpclient.NameValuePair;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;

import java.io.IOException;

public class FrameOptionsPageDecoratorTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void defaultHeaderPresent() throws IOException, SAXException {
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage page = wc.goTo("");
        Assert.assertEquals("Expected different X-Frame-Options value", getFrameOptionsFromResponse(page.getWebResponse()), "sameorigin");
    }

    @Test
    public void testDisabledFrameOptions() throws IOException, SAXException {
        FrameOptionsPageDecorator.enabled = false;
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage page = wc.goTo("");
        Assert.assertNull("Expected X-Frame-Options unset", getFrameOptionsFromResponse(page.getWebResponse()));
    }

    private static String getFrameOptionsFromResponse(WebResponse response) {
        for (NameValuePair pair : response.getResponseHeaders()) {
            if (pair.getName().equals("X-Frame-Options")) {
                return pair.getValue();
            }
        }
        return null;
    }
}
