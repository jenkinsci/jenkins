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
import hudson.Main;
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
                    htmlPage.getWebResponse().getContentAsString(),
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
                    htmlPage.getWebResponse().getContentAsString().replace("Content-Security-Policy", "{0}"),
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
                    htmlPage.getWebResponse().getContentAsString().replace("Content-Security-Policy-Report-Only", "{0}"),
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
                    htmlPage.getWebResponse().getContentAsString(),
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
                    htmlPage.getWebResponse().getContentAsString(),
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
    public void testFallback(JenkinsRule j) throws IOException, SAXException {
        // This would be more convincing with a "real" Jenkins run, but it doesn't look like JTH allows for not setting these flags.
        Main.isDevelopmentMode = false;
        Main.isUnitTest = false;
        try (JenkinsRule.WebClient webClient = j.createWebClient()) {
            final Optional<CspHeaderDecider> decider = CspHeaderDecider.getCurrentDecider();
            assertTrue(decider.isPresent());
            assertThat(decider.get(), instanceOf(FallbackDecider.class));

            assertTrue(ExtensionList.lookupSingleton(CspRecommendation.class).isActivated());

            final HtmlPage htmlPage = webClient.goTo("configureSecurity");
            assertThat(
                    // Workaround to placeholder for context path in this string
                    htmlPage.getWebResponse().getContentAsString().replace("/jenkins/", "{0}/"),
                    hasBlurb(jellyResource(FallbackDecider.class, "message.properties")));
            assertThat(htmlPage.getWebResponse().getResponseHeaderValue("Content-Security-Policy"), nullValue());
            assertThat(htmlPage.getWebResponse().getResponseHeaderValue("Content-Security-Policy-Report-Only"), not(nullValue()));

            // submit form and confirm this didn't create a config file
            htmlPage.getFormByName("config").submit(htmlPage.getFormByName("config").getButtonByName("Submit"));
            assertFalse(ExtensionList.lookupSingleton(CspConfiguration.class).getConfigFile().exists());
        } finally {
            Main.isDevelopmentMode = true;
            Main.isUnitTest = true;
        }
    }

    @Test
    public void testFallbackAdminMonitorAndSetup(JenkinsRule j) throws IOException, SAXException {
        // This needs to be done by disabling DevelopmentHeaderDecider, otherwise HtmlUnit will throw in
        // https://github.com/jenkinsci/jenkins/blob/320a149f7640d31f4fb7c4ee8eee81124cd6c588/src/main/js/components/search-bar/index.js#L93
        DevelopmentHeaderDecider.DISABLED = true;
        try (JenkinsRule.WebClient webClient = j.createWebClient()) {
            final Optional<CspHeaderDecider> decider = CspHeaderDecider.getCurrentDecider();
            assertTrue(decider.isPresent());
            assertThat(decider.get(), instanceOf(FallbackDecider.class));

            assertTrue(ExtensionList.lookupSingleton(CspRecommendation.class).isActivated());

            final HtmlPage htmlPage = webClient.goTo("manage/");
            final Page recommendationClick = htmlPage.getElementByName("more").click();
            assertThat(recommendationClick, instanceOf(HtmlPage.class));
            HtmlPage recommendationPage = (HtmlPage) recommendationClick;
            assertThat(
                    // Workaround to placeholder for context path in this string
                    recommendationPage.getUrl().getPath(),
                    is(j.contextPath + "/manage/administrativeMonitor/jenkins.security.csp.impl.CspRecommendation/"));
            assertThat(htmlPage.getWebResponse().getResponseHeaderValue("Content-Security-Policy"), nullValue());
            assertThat(htmlPage.getWebResponse().getResponseHeaderValue("Content-Security-Policy-Report-Only"), not(nullValue()));

            assertTrue(ExtensionList.lookupSingleton(CspRecommendation.class).isActivated());

            final Page setupClick = recommendationPage.getElementByName("setup").click();
            assertThat(setupClick, instanceOf(HtmlPage.class));
            final HtmlPage setupPage = (HtmlPage) setupClick;

            // Once we select an option on this page, the admin monitor gets deactivated
            assertFalse(ExtensionList.lookupSingleton(CspRecommendation.class).isActivated());

            // We can see the checkbox now
            assertThat(setupPage.getWebResponse().getContentAsString(), containsString("Enforce Content Security Policy"));
            assertThat(setupPage.getWebResponse().getResponseHeaderValue("Content-Security-Policy"), nullValue());
            assertThat(setupPage.getWebResponse().getResponseHeaderValue("Content-Security-Policy-Report-Only"), not(nullValue()));

            // check the box
            final DomElement enforceCheckbox = setupPage.getElementByName("_.enforce");
            assertThat(enforceCheckbox, instanceOf(HtmlCheckBoxInput.class));
            enforceCheckbox.click();

            // no config file yet
            assertFalse(ExtensionList.lookupSingleton(CspConfiguration.class).getConfigFile().exists());

            final Page afterSavingPage = HtmlFormUtil.submit(setupPage.getFormByName("config"), setupPage.getFormByName("config").getButtonByName("Submit"));
            assertThat(afterSavingPage, instanceOf(HtmlPage.class));
            assertThat(afterSavingPage.getUrl().getPath(), is(j.contextPath + "/manage/"));
            assertThat(afterSavingPage.getWebResponse().getResponseHeaderValue("Content-Security-Policy"), not(nullValue()));
            assertThat(afterSavingPage.getWebResponse().getResponseHeaderValue("Content-Security-Policy-Report-Only"), nullValue());

            // confirm that submitting created a config file
            assertTrue(ExtensionList.lookupSingleton(CspConfiguration.class).getConfigFile().exists());
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
