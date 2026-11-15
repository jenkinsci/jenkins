package jenkins.security.csp.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.ExtensionList;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;
import jenkins.security.csp.CspHeaderDecider;
import org.hamcrest.Matcher;
import org.htmlunit.Page;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlCheckBoxInput;
import org.htmlunit.html.HtmlFormUtil;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.junit.jupiter.WithLocalData;
import org.xml.sax.SAXException;

@WithJenkins
public class CspHeaderDeciderTest {

    @Test
    public void testDefaultInTest(JenkinsRule j) {
        try (JenkinsRule.WebClient webClient = j.createWebClient()) {
            final Optional<CspHeaderDecider> decider = CspHeaderDecider.getCurrentDecider();
            assertTrue(decider.isPresent());
            assertThat(decider.get(), instanceOf(DevelopmentHeaderDecider.class));

            assertFalse(ExtensionList.lookupSingleton(CspRecommendation.class).isActivated());

            final HtmlPage htmlPage = webClient.goTo("configureSecurity");
            assertThat(
                    htmlPage.querySelector("body").asXml(),
                    hasBlurb(jellyResource(DevelopmentHeaderDecider.class, "message.properties")));
            assertThat(htmlPage.getWebResponse().getResponseHeaderValue("Content-Security-Policy"), not(nullValue()));
            assertThat(htmlPage.getWebResponse().getResponseHeaderValue("Content-Security-Policy-Report-Only"), nullValue());

            // submit form and confirm this didn't create a config file
            htmlPage.getFormByName("config").submit(htmlPage.getFormByName("config").getButtonByName("Submit"));
            assertFalse(ExtensionList.lookupSingleton(CspConfiguration.class).getConfigFile().exists());
        } catch (IOException | SAXException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testDefaultWithSystemPropertyEnforce(JenkinsRule j) throws IOException, SAXException {
        System.setProperty(SystemPropertyHeaderDecider.SYSTEM_PROPERTY_NAME, "Content-Security-Policy");
        try (JenkinsRule.WebClient webClient = j.createWebClient()) {
            final Optional<CspHeaderDecider> decider = CspHeaderDecider.getCurrentDecider();
            assertTrue(decider.isPresent());
            assertThat(decider.get(), instanceOf(SystemPropertyHeaderDecider.class));

            assertFalse(ExtensionList.lookupSingleton(CspRecommendation.class).isActivated());

            final HtmlPage htmlPage = webClient.goTo("configureSecurity");
            assertThat(
                    htmlPage.querySelector("body").asXml().replace("Content-Security-Policy", "{0}"),
                    hasBlurb(jellyResource(SystemPropertyHeaderDecider.class, "message.properties")));
            assertThat(htmlPage.getWebResponse().getResponseHeaderValue("Content-Security-Policy"), not(nullValue()));
            assertThat(htmlPage.getWebResponse().getResponseHeaderValue("Content-Security-Policy-Report-Only"), nullValue());

            // submit form and confirm this didn't create a config file
            htmlPage.getFormByName("config").submit(htmlPage.getFormByName("config").getButtonByName("Submit"));
            assertFalse(ExtensionList.lookupSingleton(CspConfiguration.class).getConfigFile().exists());
        } finally {
            System.clearProperty(SystemPropertyHeaderDecider.SYSTEM_PROPERTY_NAME);
        }
    }

    @Test
    public void testDefaultWithSystemPropertyUnenforce(JenkinsRule j) throws IOException, SAXException {
        System.setProperty(SystemPropertyHeaderDecider.SYSTEM_PROPERTY_NAME, "Content-Security-Policy-Report-Only");
        try (JenkinsRule.WebClient webClient = j.createWebClient()) {
            final Optional<CspHeaderDecider> decider = CspHeaderDecider.getCurrentDecider();
            assertTrue(decider.isPresent());
            assertThat(decider.get(), instanceOf(SystemPropertyHeaderDecider.class));

            assertFalse(ExtensionList.lookupSingleton(CspRecommendation.class).isActivated());

            final HtmlPage htmlPage = webClient.goTo("configureSecurity");
            assertThat(
                    htmlPage.querySelector("body").asXml().replace("Content-Security-Policy-Report-Only", "{0}"),
                    hasBlurb(jellyResource(SystemPropertyHeaderDecider.class, "message.properties")));
            assertThat(htmlPage.getWebResponse().getResponseHeaderValue("Content-Security-Policy"), nullValue());
            assertThat(htmlPage.getWebResponse().getResponseHeaderValue("Content-Security-Policy-Report-Only"), not(nullValue()));

            // submit form and confirm this didn't create a config file
            htmlPage.getFormByName("config").submit(htmlPage.getFormByName("config").getButtonByName("Submit"));
            assertFalse(ExtensionList.lookupSingleton(CspConfiguration.class).getConfigFile().exists());
        } finally {
            System.clearProperty(SystemPropertyHeaderDecider.SYSTEM_PROPERTY_NAME);
        }
    }

    @Test
    public void testDefaultWithSystemPropertyNone(JenkinsRule j) throws IOException, SAXException {
        System.setProperty(SystemPropertyHeaderDecider.SYSTEM_PROPERTY_NAME, "");
        try (JenkinsRule.WebClient webClient = j.createWebClient()) {
            final Optional<CspHeaderDecider> decider = CspHeaderDecider.getCurrentDecider();
            assertTrue(decider.isPresent());
            assertThat(decider.get(), instanceOf(SystemPropertyHeaderDecider.class));

            assertFalse(ExtensionList.lookupSingleton(CspRecommendation.class).isActivated());

            final HtmlPage htmlPage = webClient.goTo("configureSecurity");
            assertThat(
                    htmlPage.querySelector("body").asXml(),
                    hasMessage(jellyResource(SystemPropertyHeaderDecider.class, "message.properties"), "blurbUnset"));
            assertThat(htmlPage.getWebResponse().getResponseHeaderValue("Content-Security-Policy"), nullValue());
            assertThat(htmlPage.getWebResponse().getResponseHeaderValue("Content-Security-Policy-Report-Only"), nullValue());

            // submit form and confirm this didn't create a config file
            htmlPage.getFormByName("config").submit(htmlPage.getFormByName("config").getButtonByName("Submit"));
            assertFalse(ExtensionList.lookupSingleton(CspConfiguration.class).getConfigFile().exists());
        } finally {
            System.clearProperty(SystemPropertyHeaderDecider.SYSTEM_PROPERTY_NAME);
        }
    }

    @Test
    public void testDefaultWithSystemPropertyWrong(JenkinsRule j) throws IOException, SAXException {
        System.setProperty(SystemPropertyHeaderDecider.SYSTEM_PROPERTY_NAME, "Some-Other-Value");
        try (JenkinsRule.WebClient webClient = j.createWebClient()) {
            final Optional<CspHeaderDecider> decider = CspHeaderDecider.getCurrentDecider();
            assertTrue(decider.isPresent());
            assertThat(decider.get(), instanceOf(DevelopmentHeaderDecider.class));

            assertFalse(ExtensionList.lookupSingleton(CspRecommendation.class).isActivated());

            final HtmlPage htmlPage = webClient.goTo("configureSecurity");
            assertThat(
                    htmlPage.querySelector("body").asXml(),
                    allOf(
                            hasBlurb(jellyResource(DevelopmentHeaderDecider.class, "message.properties")),
                            not(hasBlurb(jellyResource(SystemPropertyHeaderDecider.class, "message.properties")))));
            assertThat(htmlPage.getWebResponse().getResponseHeaderValue("Content-Security-Policy"), not(nullValue()));
            assertThat(htmlPage.getWebResponse().getResponseHeaderValue("Content-Security-Policy-Report-Only"), nullValue());

            // submit form and confirm this didn't create a config file
            htmlPage.getFormByName("config").submit(htmlPage.getFormByName("config").getButtonByName("Submit"));
            assertFalse(ExtensionList.lookupSingleton(CspConfiguration.class).getConfigFile().exists());
        } finally {
            System.clearProperty(SystemPropertyHeaderDecider.SYSTEM_PROPERTY_NAME);
        }
    }

    @Test
    public void testAdminMonitorAndSetup(JenkinsRule j) throws IOException, SAXException {
        // This needs to be done by disabling DevelopmentHeaderDecider, otherwise HtmlUnit will throw in
        // https://github.com/jenkinsci/jenkins/blob/320a149f7640d31f4fb7c4ee8eee81124cd6c588/src/main/js/components/search-bar/index.js#L93
        DevelopmentHeaderDecider.DISABLED = true;
        try (JenkinsRule.WebClient webClient = j.createWebClient()) {
            final Optional<CspHeaderDecider> decider = CspHeaderDecider.getCurrentDecider();
            assertTrue(decider.isPresent());
            assertThat(decider.get(), instanceOf(CspConfiguration.ConfigurationHeaderDecider.class));

            assertTrue(ExtensionList.lookupSingleton(CspRecommendation.class).isActivated());

            final HtmlPage managePage = webClient.goTo("manage/");
            // CSP is enforced by default (enforce==null treated as true)
            assertThat(managePage.getWebResponse().getResponseHeaderValue("Content-Security-Policy"), not(nullValue()));
            assertThat(managePage.getWebResponse().getResponseHeaderValue("Content-Security-Policy-Report-Only"), nullValue());

            // Click "Go to Security configuration" button in the admin monitor
            DomElement setupButton = managePage.querySelector("[data-monitor-id='jenkins.security.csp.impl.CspRecommendation'] [name='setup']");
            final Page configureSecurityPage = setupButton.click();
            assertThat(configureSecurityPage, instanceOf(HtmlPage.class));
            final HtmlPage setupPage = (HtmlPage) configureSecurityPage;

            // Checkbox is visible and checked (CSP enforced by default)
            assertThat(setupPage.querySelector("body").asXml(), containsString("Enforce Content Security Policy"));
            final DomElement enforceCheckbox = setupPage.getElementByName("_.enforce");
            assertThat(enforceCheckbox, instanceOf(HtmlCheckBoxInput.class));
            assertTrue(((HtmlCheckBoxInput) enforceCheckbox).isChecked());

            // No config file yet
            assertFalse(ExtensionList.lookupSingleton(CspConfiguration.class).getConfigFile().exists());

            // Submit the form (checkbox is checked by default, saves enforce=true)
            final Page afterSavingPage = HtmlFormUtil.submit(setupPage.getFormByName("config"), setupPage.getFormByName("config").getButtonByName("Submit"));
            assertThat(afterSavingPage, instanceOf(HtmlPage.class));
            assertThat(afterSavingPage.getUrl().getPath(), is(j.contextPath + "/manage/configureSecurity/"));
            assertThat(afterSavingPage.getWebResponse().getResponseHeaderValue("Content-Security-Policy"), not(nullValue()));
            assertThat(afterSavingPage.getWebResponse().getResponseHeaderValue("Content-Security-Policy-Report-Only"), nullValue());

            // Config file is now created and monitor is deactivated
            assertTrue(ExtensionList.lookupSingleton(CspConfiguration.class).getConfigFile().exists());
            assertFalse(ExtensionList.lookupSingleton(CspRecommendation.class).isActivated());
        } finally {
            DevelopmentHeaderDecider.DISABLED = false;
        }
    }

    @Test
    @WithLocalData
    public void testExistingEnforceTrue(JenkinsRule j) throws IOException, SAXException {
        DevelopmentHeaderDecider.DISABLED = true;
        try (JenkinsRule.WebClient webClient = j.createWebClient()) {
            // Confirm local data
            assertTrue(ExtensionList.lookupSingleton(CspConfiguration.class).getConfigFile().exists());
            assertTrue(ExtensionList.lookupSingleton(CspConfiguration.class).isEnforce());

            // No admin monitor with explicit configuration
            assertFalse(ExtensionList.lookupSingleton(CspRecommendation.class).isActivated());

            // ConfigurationHeaderDecider is active
            final Optional<CspHeaderDecider> decider = CspHeaderDecider.getCurrentDecider();
            assertTrue(decider.isPresent());
            assertThat(decider.get(), instanceOf(CspConfiguration.ConfigurationHeaderDecider.class));

            // CSP is enforced
            final HtmlPage htmlPage = webClient.goTo("configureSecurity");
            assertThat(htmlPage.getWebResponse().getResponseHeaderValue("Content-Security-Policy"), not(nullValue()));
            assertThat(htmlPage.getWebResponse().getResponseHeaderValue("Content-Security-Policy-Report-Only"), nullValue());

            // Checkbox is shown and checked
            final DomElement enforceCheckbox = htmlPage.getElementByName("_.enforce");
            assertThat(enforceCheckbox, instanceOf(HtmlCheckBoxInput.class));
            assertTrue(((HtmlCheckBoxInput) enforceCheckbox).isChecked());
        } finally {
            DevelopmentHeaderDecider.DISABLED = false;
        }
    }

    @Test
    @WithLocalData
    public void testExistingEnforceFalse(JenkinsRule j) throws IOException, SAXException {
        DevelopmentHeaderDecider.DISABLED = true;
        try (JenkinsRule.WebClient webClient = j.createWebClient()) {
            // Confirm local data
            assertTrue(ExtensionList.lookupSingleton(CspConfiguration.class).getConfigFile().exists());
            assertFalse(ExtensionList.lookupSingleton(CspConfiguration.class).isEnforce());

            // No admin monitor with explicit configuration
            assertFalse(ExtensionList.lookupSingleton(CspRecommendation.class).isActivated());

            // ConfigurationHeaderDecider is active
            final Optional<CspHeaderDecider> decider = CspHeaderDecider.getCurrentDecider();
            assertTrue(decider.isPresent());
            assertThat(decider.get(), instanceOf(CspConfiguration.ConfigurationHeaderDecider.class));

            // CSP is report-only (not enforced)
            final HtmlPage htmlPage = webClient.goTo("configureSecurity");
            assertThat(htmlPage.getWebResponse().getResponseHeaderValue("Content-Security-Policy"), nullValue());
            assertThat(htmlPage.getWebResponse().getResponseHeaderValue("Content-Security-Policy-Report-Only"), not(nullValue()));

            // Checkbox is shown and unchecked
            final DomElement enforceCheckbox = htmlPage.getElementByName("_.enforce");
            assertThat(enforceCheckbox, instanceOf(HtmlCheckBoxInput.class));
            assertFalse(((HtmlCheckBoxInput) enforceCheckbox).isChecked());
        } finally {
            DevelopmentHeaderDecider.DISABLED = false;
        }
    }

    private static Matcher<String> hasBlurb(Properties props) {
        return hasMessage(props, "blurb");
    }

    private static Matcher<String> hasMessage(Properties props, String key) {
        return containsString(props.getProperty(key));
    }

    private static Properties jellyResource(Class<?> clazz, String filename) throws IOException {
        Properties props = new Properties();
        props.load(clazz.getResourceAsStream(clazz.getSimpleName() + "/" + filename));
        return props;
    }
}
