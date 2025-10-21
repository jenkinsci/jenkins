package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.ExtensionList;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import jenkins.security.ResourceDomainConfiguration;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.Page;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ValueSource;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.Dispatcher;

@ParameterizedClass
@ValueSource(strings = { "/jenkins", "" })
@WithJenkins
class ErrorPageTest {

    @Parameter
    private String contextPath;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Throwable {
        j = rule;

        j.contextPath = contextPath;
        j.restart();
    }

    @Test
    @Issue("JENKINS-71087")
    void nice404ErrorPage() throws Exception {
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            Dispatcher.TRACE = false;

            /* Start with no security realm configured */

            { // basic error page
                final FailingHttpStatusCodeException ex = assertThrows(FailingHttpStatusCodeException.class, () -> wc.goTo("foo"));
                assertEquals(404, ex.getStatusCode());
                final String content = ex.getResponse().getContentAsString(StandardCharsets.UTF_8);
                assertThat(content, not(containsString(j.contextPath + "/login?from=")));
                assertThat(content, containsString("This page does not exist."));
                assertThat(content, not(containsString("REST API")));
            }

            { // paths are fine on error page even when nested
                final FailingHttpStatusCodeException ex = assertThrows(FailingHttpStatusCodeException.class, () -> wc.goTo("foo/bar/baz/"));
                assertEquals(404, ex.getStatusCode());
                final String content = ex.getResponse().getContentAsString(StandardCharsets.UTF_8);
                assertThat(content, not(containsString(j.contextPath + "/login?from=")));
                assertThat(content, containsString("This page does not exist."));
                assertThat(content, not(containsString("REST API")));
            }

            { // resource root action have custom (less) error message content
                final FailingHttpStatusCodeException ex = assertThrows(FailingHttpStatusCodeException.class, () -> wc.goTo("static-files/foo"));
                assertEquals(404, ex.getStatusCode());
                final String content = ex.getResponse().getContentAsString(StandardCharsets.UTF_8);
                assertThat(content, not(containsString(j.contextPath + "/login?from=")));
                assertThat(content, not(containsString("This page does not exist.")));
                assertThat(content, not(containsString("This page may not exist, or you may not have permission to see it.")));
                assertThat(content, not(containsString("REST API")));
            }

            /* Set up security realm and request as anonymous, we expect login link and hedged response */
            j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
            j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().toAuthenticated().grant(Jenkins.READ).everywhere().toEveryone());

            { // basic error page
                final FailingHttpStatusCodeException ex = assertThrows(FailingHttpStatusCodeException.class, () -> wc.goTo("foo"));
                assertEquals(404, ex.getStatusCode());
                final String content = ex.getResponse().getContentAsString(StandardCharsets.UTF_8);
                assertThat(content, containsString(j.contextPath + "/login?from=" + j.contextPath.replace("/", "%2F") + "%2Ffoo"));
                assertThat(content, not(containsString(j.contextPath + "/login?from=" + j.contextPath.replace("/", "%2F") + "%2F404")));
                assertThat(content, containsString("This page may not exist, or you may not have permission to see it."));
                assertThat(content, not(containsString("REST API")));
            }

            { // paths are fine on error page even when nested
                final FailingHttpStatusCodeException ex = assertThrows(FailingHttpStatusCodeException.class, () -> wc.goTo("foo/bar/baz/"));
                assertEquals(404, ex.getStatusCode());
                final String content = ex.getResponse().getContentAsString(StandardCharsets.UTF_8);
                assertThat(content, containsString(j.contextPath + "/login?from=" + j.contextPath.replace("/", "%2F") + "%2Ffoo%2Fbar%2Fbaz%2F"));
                assertThat(content, not(containsString(j.contextPath + "/login?from=" + j.contextPath.replace("/", "%2F") + "%2F404")));
                assertThat(content, containsString("This page may not exist, or you may not have permission to see it."));
                assertThat(content, not(containsString("REST API")));
            }

            { // resource root action have custom (less) error message content
                final FailingHttpStatusCodeException ex = assertThrows(FailingHttpStatusCodeException.class, () -> wc.goTo("static-files/foo"));
                assertEquals(404, ex.getStatusCode());
                final String content = ex.getResponse().getContentAsString(StandardCharsets.UTF_8);
                assertThat(content, containsString(j.contextPath + "/login?from=" + j.contextPath.replace("/", "%2F") + "%2Fstatic-files%2Ffoo"));
                assertThat(content, not(containsString(j.contextPath + "/login?from=" + j.contextPath.replace("/", "%2F") + "%2F404")));
                assertThat(content, not(containsString("This page does not exist.")));
                assertThat(content, not(containsString("This page may not exist, or you may not have permission to see it.")));
                assertThat(content, not(containsString("REST API")));
            }

            /* With the security realm still set up, log in and expect the profile link to show */
            wc.login("alice");

            { // basic error page
                final FailingHttpStatusCodeException ex = assertThrows(FailingHttpStatusCodeException.class, () -> wc.goTo("foo"));
                assertEquals(404, ex.getStatusCode());
                final String content = ex.getResponse().getContentAsString(StandardCharsets.UTF_8);
                assertThat(content, not(containsString(j.contextPath + "/login?from=")));
                assertThat(content, containsString("user/alice"));
                assertThat(content, containsString("This page may not exist, or you may not have permission to see it."));
                assertThat(content, not(containsString("REST API")));
            }

            { // paths are fine on error page even when nested
                final FailingHttpStatusCodeException ex = assertThrows(FailingHttpStatusCodeException.class, () -> wc.goTo("foo/bar/baz/"));
                assertEquals(404, ex.getStatusCode());
                final String content = ex.getResponse().getContentAsString(StandardCharsets.UTF_8);
                assertThat(content, not(containsString(j.contextPath + "/login?from=")));
                assertThat(content, containsString("user/alice"));
                assertThat(content, containsString("This page may not exist, or you may not have permission to see it."));
                assertThat(content, not(containsString("REST API")));
            }

            { // resource root action have custom (less) error message content
                final FailingHttpStatusCodeException ex = assertThrows(FailingHttpStatusCodeException.class, () -> wc.goTo("static-files/foo"));
                assertEquals(404, ex.getStatusCode());
                final String content = ex.getResponse().getContentAsString(StandardCharsets.UTF_8);
                assertThat(content, not(containsString(j.contextPath + "/login?from=")));
                assertThat(content, containsString("user/alice"));
                assertThat(content, not(containsString("This page does not exist.")));
                assertThat(content, not(containsString("This page may not exist, or you may not have permission to see it.")));
                assertThat(content, not(containsString("REST API")));
            }
        } finally {
            Dispatcher.TRACE = true;
        }
    }

    @Test
    @Issue("JENKINS-71087")
    void kindaNice404ErrorPageOnResourceDomain() throws Exception {
        final String resourceRoot;
        { // Setup stolen from ResourceDomainTest
            URL root = j.getURL(); // which always will use "localhost", see JenkinsRule#getURL()
            assertTrue(root.toString().contains("localhost")); // to be safe

            resourceRoot = root.toString().replace("localhost", "127.0.0.1");
            ResourceDomainConfiguration configuration = ExtensionList.lookupSingleton(ResourceDomainConfiguration.class);
            configuration.setUrl(resourceRoot);
        }

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wc.setThrowExceptionOnFailingStatusCode(false);

            @SuppressWarnings("deprecation") // we need to not access the usual Jenkins URLs
            final Page page = wc.getPage(resourceRoot + "foo");
            wc.setThrowExceptionOnFailingStatusCode(true);

            assertEquals(404, page.getWebResponse().getStatusCode());
            final String content = page.getWebResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(content, containsString("Back to Jenkins"));
            assertThat(content, containsString("Jenkins serves only static files on this domain."));
            assertThat(content, not(containsString("REST API")));
            if (page.isHtmlPage()) {
                final HtmlPage htmlPage = (HtmlPage) page;
                final Page nextPage = htmlPage.getAnchorByText("Back to Jenkins").click();
                final String nextContent = nextPage.getWebResponse().getContentAsString(StandardCharsets.UTF_8);
                assertThat(nextContent, containsString("Welcome to Jenkins"));
                assertThat(nextContent, containsString("REST API")); // Rest API exists for Jenkins main page
            }
        }
    }
}
