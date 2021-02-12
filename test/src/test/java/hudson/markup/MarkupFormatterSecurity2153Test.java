package hudson.markup;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.net.URL;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

// TODO merge back into MarkupFormatterTest
public class MarkupFormatterSecurity2153Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void security2153RequiresPOST() throws Exception {
        final JenkinsRule.WebClient wc = j.createWebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);
        final HtmlPage htmlPage = wc.goTo("markupFormatter/previewDescription?text=lolwut");
        final WebResponse response = htmlPage.getWebResponse();
        assertEquals(405, response.getStatusCode());
        assertThat(response.getContentAsString(), containsString("This endpoint now requires that POST requests are sent"));
        assertThat(response.getContentAsString(), not(containsString("lolwut")));
    }

    @Test
    public void security2153SetsCSP() throws Exception {
        final JenkinsRule.WebClient wc = j.createWebClient();
        final Page htmlPage = wc.getPage(wc.addCrumb(new WebRequest(new URL(j.jenkins.getRootUrl() + "/markupFormatter/previewDescription?text=lolwut"), HttpMethod.POST)));
        final WebResponse response = htmlPage.getWebResponse();
        assertEquals(200, response.getStatusCode());
        assertThat(response.getContentAsString(), containsString("lolwut"));
        assertThat(response.getResponseHeaderValue("Content-Security-Policy"), containsString("default-src 'none';"));
        assertThat(response.getResponseHeaderValue("X-Content-Security-Policy"), containsString("default-src 'none';"));
        assertThat(response.getResponseHeaderValue("X-WebKit-CSP"), containsString("default-src 'none';"));
    }
}
