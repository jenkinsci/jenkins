package hudson.model;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class FileParameterValueSecurity1793Test {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Issue("SECURITY-1793")
    @Test
    @LocalData
    public void contentSecurityPolicy() throws Exception {
        FreeStyleProject p = j.jenkins.getItemByFullName("SECURITY-1793", FreeStyleProject.class);

        HtmlPage page = j.createWebClient().goTo("job/" + p.getName() + "/lastSuccessfulBuild/parameters/parameter/html.html/html.html");
        for (String header : new String[]{"Content-Security-Policy", "X-WebKit-CSP", "X-Content-Security-Policy"}) {
            assertEquals("Header set: " + header, DirectoryBrowserSupport.DEFAULT_CSP_VALUE, page.getWebResponse().getResponseHeaderValue(header));
        }

        String propName = DirectoryBrowserSupport.class.getName() + ".CSP";
        String initialValue = System.getProperty(propName);
        try {
            System.setProperty(propName, "");
            page = j.createWebClient().goTo("job/" + p.getName() + "/lastSuccessfulBuild/parameters/parameter/html.html/html.html");
            for (String header : new String[]{"Content-Security-Policy", "X-WebKit-CSP", "X-Content-Security-Policy"}) {
                assertFalse("Header not set: " + header, page.getWebResponse().getResponseHeaders().contains(header));
            }
        } finally {
            if (initialValue == null) {
                System.clearProperty(DirectoryBrowserSupport.class.getName() + ".CSP");
            } else {
                System.setProperty(DirectoryBrowserSupport.class.getName() + ".CSP", initialValue);
            }
        }
    }

}
