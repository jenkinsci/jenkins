package jenkins.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
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
        assertEquals("Expected different X-Frame-Options value", getFrameOptionsFromResponse(page.getWebResponse()), "sameorigin");
    }

    @Test
    public void testDisabledFrameOptions() throws IOException, SAXException {
        FrameOptionsPageDecorator.enabled = false;
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage page = wc.goTo("");
        assertNull("Expected X-Frame-Options unset", getFrameOptionsFromResponse(page.getWebResponse()));
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
