package hudson.model;

import com.gargoylesoftware.htmlunit.FormEncodingType;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.net.URLEncoder;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ViewSEC1871Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("SECURITY-1871")
    public void shouldNotAllowInconsistentViewName() throws IOException {
        assertNull(j.jenkins.getView("ViewName"));
        JenkinsRule.WebClient wc = j.createWebClient();
        WebRequest req = new WebRequest(wc.createCrumbedUrl("createView"), HttpMethod.POST);
        req.setEncodingType(FormEncodingType.URL_ENCODED);
        req.setRequestBody("name=ViewName&mode=hudson.model.ListView&json=" + URLEncoder.encode("{\"mode\":\"hudson.model.ListView\",\"name\":\"DifferentViewName\"}", "UTF-8"));
        wc.getPage(req);
        assertNull(j.jenkins.getView("DifferentViewName"));
        assertNotNull(j.jenkins.getView("ViewName"));
    }
}
