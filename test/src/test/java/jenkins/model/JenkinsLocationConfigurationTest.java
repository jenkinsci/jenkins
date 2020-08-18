package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlElementUtil;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlFormUtil;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import junit.framework.AssertionFailedError;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Kohsuke Kawaguchi
 */
public class JenkinsLocationConfigurationTest {

    private String lastRootUrlReturned;
    private boolean lastRootUrlSet;

    @Rule
    public JenkinsRule j = new JenkinsRule(){
        @Override
        public URL getURL() throws IOException {
            // first call for the "Running on xxx" log message, Jenkins not being set at that point
            // and the second call is to set the rootUrl of the JLC inside the JenkinsRule#init
            if (Jenkins.getInstanceOrNull() != null) {
                // only useful for doNotAcceptNonHttpBasedRootURL_fromConfigXml
                lastRootUrlReturned = JenkinsLocationConfiguration.getOrDie().getUrl();
                lastRootUrlSet = true;
            }
            return super.getURL();
        }
    };
    
    /**
     * Makes sure the use of "localhost" in the Hudson URL reports a warning.
     */
    @Test
    public void localhostWarning() throws Exception {
        HtmlPage p = j.createWebClient().goTo("configure");
        HtmlInput url = p.getFormByName("config").getInputByName("_.url");
        url.setValueAttribute("http://localhost:1234/");
        assertThat(p.getDocumentElement().getTextContent(), containsString("instead of localhost"));
    }

    @Test
    @Issue("SECURITY-1471")
    public void doNotAcceptNonHttpBasedRootURL_fromUI() throws Exception {
        // in JenkinsRule, the URL is set to the current URL
        JenkinsLocationConfiguration.getOrDie().setUrl(null);

        JenkinsRule.WebClient wc = j.createWebClient();

        assertNull(JenkinsLocationConfiguration.getOrDie().getUrl());

        settingRootURL("javascript:alert(123);//");

        // no impact on the url in memory
        assertNull(JenkinsLocationConfiguration.getOrDie().getUrl());

        File configFile = new File(j.jenkins.getRootDir(), "jenkins.model.JenkinsLocationConfiguration.xml");
        String configFileContent = FileUtils.readFileToString(configFile, StandardCharsets.UTF_8);
        assertThat(configFileContent, containsString("JenkinsLocationConfiguration"));
        assertThat(configFileContent, not(containsString("javascript:alert(123);//")));
    }

    @Test
    @Issue("SECURITY-1471")
    public void escapeHatch_acceptNonHttpBasedRootURL_fromUI() throws Exception {
        boolean previousValue = JenkinsLocationConfiguration.DISABLE_URL_VALIDATION;
        JenkinsLocationConfiguration.DISABLE_URL_VALIDATION = true;

        try {
            // in JenkinsRule, the URL is set to the current URL
            JenkinsLocationConfiguration.getOrDie().setUrl(null);

            JenkinsRule.WebClient wc = j.createWebClient();

            assertNull(JenkinsLocationConfiguration.getOrDie().getUrl());

            String expectedUrl = "weirdSchema:somethingAlsoWeird";
            settingRootURL(expectedUrl);

            // the method ensures there is an trailing slash
            assertEquals(expectedUrl + "/", JenkinsLocationConfiguration.getOrDie().getUrl());

            File configFile = new File(j.jenkins.getRootDir(), "jenkins.model.JenkinsLocationConfiguration.xml");
            String configFileContent = FileUtils.readFileToString(configFile, StandardCharsets.UTF_8);
            assertThat(configFileContent, containsString("JenkinsLocationConfiguration"));
            assertThat(configFileContent, containsString(expectedUrl));
        }
        finally {
            JenkinsLocationConfiguration.DISABLE_URL_VALIDATION = previousValue;
        }
    }

    @Test
    @Issue("SECURITY-1471")
    @LocalData("xssThroughConfigXml")
    public void doNotAcceptNonHttpBasedRootURL_fromConfigXml() {
        // in JenkinsRule, the URL is set to the current URL, even if coming from LocalData
        // so we need to catch the last value before the getUrl from the JenkinsRule that will be used to set the rootUrl
        assertNull(lastRootUrlReturned);
        assertTrue(lastRootUrlSet);

        assertThat(JenkinsLocationConfiguration.getOrDie().getUrl(), not(containsString("javascript")));
    }

    @Test
    @Issue("SECURITY-1471")
    public void cannotInjectJavaScriptUsingRootUrl_inNewViewLinkAction() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();

        settingRootURL("javascript:alert(123);//");

        // setup the victim
        AtomicReference<Boolean> alertAppeared = new AtomicReference<>(false);
        wc.setAlertHandler((page, s) -> alertAppeared.set(true));
        HtmlPage page = wc.goTo("");

        HtmlAnchor newViewLink = page.getDocumentElement().getElementsByTagName("a").stream()
                .filter(HtmlAnchor.class::isInstance).map(HtmlAnchor.class::cast)
                .filter(a -> a.getHrefAttribute().endsWith("newView"))
                .findFirst().orElseThrow(AssertionFailedError::new);

        // last verification
        assertFalse(alertAppeared.get());

        HtmlElementUtil.click(newViewLink);

        assertFalse(alertAppeared.get());
    }

    @Test
    @Issue("SECURITY-1471")
    public void cannotInjectJavaScriptUsingRootUrl_inLabelAbsoluteLink() throws Exception {
        String masterLabel = "master-node";
        j.jenkins.setLabelString(masterLabel);

        JenkinsRule.WebClient wc = j.createWebClient();

        settingRootURL("javascript:alert(123);//");

        // setup the victim
        AtomicReference<Boolean> alertAppeared = new AtomicReference<>(false);
        wc.setAlertHandler((page, s) -> alertAppeared.set(true));

        FreeStyleProject p = j.createFreeStyleProject();
        p.setAssignedLabel(Label.get(masterLabel));

        HtmlPage projectConfigurePage = wc.getPage(p, "/configure");

        HtmlAnchor labelAnchor = projectConfigurePage.getDocumentElement().getElementsByTagName("a").stream()
                .filter(HtmlAnchor.class::isInstance).map(HtmlAnchor.class::cast)
                .filter(a -> a.getHrefAttribute().contains("/label/"))
                .findFirst().orElseThrow(AssertionFailedError::new);

        assertFalse(alertAppeared.get());
        HtmlElementUtil.click(labelAnchor);
        assertFalse(alertAppeared.get());

        String labelHref = labelAnchor.getHrefAttribute();
        assertThat(labelHref, not(containsString("javascript:alert(123)")));

        String responseContent = projectConfigurePage.getWebResponse().getContentAsString();
        assertThat(responseContent, not(containsString("javascript:alert(123)")));
    }

    private void settingRootURL(String desiredRootUrl) throws Exception {
        HtmlPage configurePage = j.createWebClient().goTo("configure");
        HtmlForm configForm = configurePage.getFormByName("config");
        HtmlInput url = configForm.getInputByName("_.url");
        url.setValueAttribute(desiredRootUrl);
        HtmlFormUtil.submit(configForm);
    }
}
