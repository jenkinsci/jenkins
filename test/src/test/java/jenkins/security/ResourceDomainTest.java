package jenkins.security;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Functions;
import hudson.model.DirectoryBrowserSupport;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.UnprotectedRootAction;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.UUID;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.Page;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.CreateFileBuilder;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.HttpResponse;

@Issue("JENKINS-41891")
@For({ ResourceDomainRootAction.class, ResourceDomainFilter.class, ResourceDomainConfiguration.class })
@WithJenkins
class ResourceDomainTest {

    private static final String RESOURCE_DOMAIN = "127.0.0.1";

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        j = rule;
        String resourceRoot;
        URL root = j.getURL(); // which always will use "localhost", see JenkinsRule#getURL()
        assertTrue(root.toString().contains("localhost")); // to be safe

        resourceRoot = root.toString().replace("localhost", RESOURCE_DOMAIN);
        ResourceDomainConfiguration configuration = ExtensionList.lookupSingleton(ResourceDomainConfiguration.class);
        configuration.setUrl(resourceRoot);
    }

    @Test
    void groupPermissionsWork() throws Exception {
        final JenkinsRule.DummySecurityRealm securityRealm = j.createDummySecurityRealm();
        securityRealm.addGroups("alice", "admins");
        j.jenkins.setSecurityRealm(securityRealm);
        MockAuthorizationStrategy a = new MockAuthorizationStrategy().grant(Jenkins.READ).everywhere().to("admins");
        j.jenkins.setAuthorizationStrategy(a);

        JenkinsRule.WebClient webClient = j.createWebClient().login("alice");

        { // DBS directory listing is shown as always
            Page page = webClient.goTo("userContent");
            assertEquals(200, page.getWebResponse().getStatusCode(), "successful request");
            assertTrue(page.getUrl().toString().contains("/userContent"), "still on the original URL");
            assertTrue(page.isHtmlPage(), "web page");
            assertTrue(page.getWebResponse().getContentAsString().contains("javascript"), "complex web page");
        }
        { // DBS on primary domain forwards to second domain when trying to access a file URL
            webClient.setRedirectEnabled(true);
            Page page = webClient.goTo("userContent/readme.txt", "text/plain");
            final String resourceResponseUrl = page.getUrl().toString();
            assertEquals(200, page.getWebResponse().getStatusCode(), "resource response success");
            assertNull(page.getWebResponse().getResponseHeaderValue("Content-Security-Policy"), "no CSP headers");
            assertTrue(resourceResponseUrl.contains(RESOURCE_DOMAIN), "Served from resource domain");
            assertTrue(resourceResponseUrl.contains("static-files"), "Served from resource action");
        }
    }

    @Test
    void secondDomainBasics() throws Exception {
        JenkinsRule.WebClient webClient = j.createWebClient();

        { // DBS directory listing is shown as always
            Page page = webClient.goTo("userContent");
            assertEquals(200, page.getWebResponse().getStatusCode(), "successful request");
            assertTrue(page.getUrl().toString().contains("/userContent"), "still on the original URL");
            assertTrue(page.isHtmlPage(), "web page");
            assertTrue(page.getWebResponse().getContentAsString().contains("javascript"), "complex web page");
        }

        String resourceResponseUrl;
        { // DBS on primary domain forwards to second domain when trying to access a file URL
            webClient.setRedirectEnabled(true);
            Page page = webClient.goTo("userContent/readme.txt", "text/plain");
            resourceResponseUrl = page.getUrl().toString();
            assertEquals(200, page.getWebResponse().getStatusCode(), "resource response success");
            assertNull(page.getWebResponse().getResponseHeaderValue("Content-Security-Policy"), "no CSP headers");
            assertTrue(resourceResponseUrl.contains(RESOURCE_DOMAIN), "Served from resource domain");
            assertTrue(resourceResponseUrl.contains("static-files"), "Served from resource action");
        }

        { // direct access to resource URL works
            Page page = webClient.getPage(resourceResponseUrl);
            resourceResponseUrl = page.getUrl().toString();
            assertEquals(200, page.getWebResponse().getStatusCode(), "resource response success");
            assertNull(page.getWebResponse().getResponseHeaderValue("Content-Security-Policy"), "no CSP headers");
            assertTrue(resourceResponseUrl.contains(RESOURCE_DOMAIN), "Served from resource domain");
            assertTrue(resourceResponseUrl.contains("static-files"), "Served from resource action");
        }

        { // show directory index
            webClient.setRedirectEnabled(false);
            webClient.setThrowExceptionOnFailingStatusCode(false);
            Page page = webClient.getPage(resourceResponseUrl.replace("readme.txt", ""));
            assertEquals(200, page.getWebResponse().getStatusCode(), "directory listing response");
            String responseContent = page.getWebResponse().getContentAsString();
            assertTrue(responseContent.contains("readme.txt"), "directory listing shown");
            assertTrue(responseContent.contains("href="), "is HTML");
        }

        String resourceRootUrl = ResourceDomainConfiguration.get().getUrl();
        {
            webClient.setThrowExceptionOnFailingStatusCode(false);
            Page page = webClient.getPage(resourceRootUrl);
            assertEquals(404, page.getWebResponse().getStatusCode(), "resource root URL response is 404");
        }

        {
            webClient.setThrowExceptionOnFailingStatusCode(false);
            Page page = webClient.getPage(resourceRootUrl + "/static-files/");
            assertEquals(404, page.getWebResponse().getStatusCode(), "resource action index page response is 404");
        }

        { // second domain invalid URL gets 404
            webClient.setThrowExceptionOnFailingStatusCode(false);
            String uuid = UUID.randomUUID().toString();
            Page page = webClient.getPage(resourceRootUrl + "static-files/" + uuid);
            assertEquals(404, page.getWebResponse().getStatusCode(), "resource response is 404");
            assertTrue(page.getUrl().toString().contains(uuid), "response URL is still the same");
        }

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy a = new MockAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(a);

        { // fails without Overall/Read
            webClient.withRedirectEnabled(false).withThrowExceptionOnFailingStatusCode(false);
            Page page = webClient.getPage(resourceResponseUrl);
            resourceResponseUrl = page.getUrl().toString();
            assertEquals(403, page.getWebResponse().getStatusCode(), "resource response failed");
            assertNull(page.getWebResponse().getResponseHeaderValue("Content-Security-Policy"), "no CSP headers");
            assertTrue(resourceResponseUrl.contains(RESOURCE_DOMAIN), "Served from resource domain");
        }

        a.grant(Jenkins.READ).onRoot().to("anonymous");

        { // now it works again
            Page page = webClient.getPage(resourceResponseUrl);
            resourceResponseUrl = page.getUrl().toString();
            assertEquals(200, page.getWebResponse().getStatusCode(), "resource response success");
            assertNull(page.getWebResponse().getResponseHeaderValue("Content-Security-Policy"), "no CSP headers");
            assertTrue(resourceResponseUrl.contains(RESOURCE_DOMAIN), "Served from resource domain");
            assertTrue(resourceResponseUrl.contains("static-files"), "Served from resource action");
        }
    }

    @Test
    void clearRootUrl() throws Exception {
        JenkinsLocationConfiguration.get().setUrl(null);

        JenkinsRule.WebClient webClient = j.createWebClient();

        String resourceResponseUrl;
        {
            webClient.setRedirectEnabled(true);
            Page page = webClient.goTo("userContent/readme.txt", "text/plain");
            resourceResponseUrl = page.getUrl().toString();
            assertEquals(200, page.getWebResponse().getStatusCode(), "resource response success");
            assertNotNull(page.getWebResponse().getResponseHeaderValue("Content-Security-Policy"), "CSP headers set");
            assertFalse(resourceResponseUrl.contains(RESOURCE_DOMAIN), "Not served from resource domain");
            assertFalse(resourceResponseUrl.contains("static-files"), "Not served from resource action");
            assertTrue(resourceResponseUrl.contains("userContent/readme.txt"), "Original URL");
        }

    }

    @Test
    void secondDomainCannotBeFaked() throws Exception {
        JenkinsRule.WebClient webClient = j.createWebClient();

        String resourceResponseUrl;
        { // first, obtain a resource response URL
            webClient.setRedirectEnabled(true);
            webClient.setThrowExceptionOnFailingStatusCode(false);
            Page page = webClient.goTo("userContent/readme.txt", "text/plain");
            resourceResponseUrl = page.getUrl().toString();
            assertEquals(200, page.getWebResponse().getStatusCode(), "resource response success");
            assertNull(page.getWebResponse().getResponseHeaderValue("Content-Security-Policy"), "no CSP headers");
            assertTrue(resourceResponseUrl.contains(RESOURCE_DOMAIN), "Served from resource domain");
            assertTrue(resourceResponseUrl.contains("static-files"), "Served from resource action");
        }

        {
            // now, modify its prefix to have an invalid HMAC
            String modifiedUrl = resourceResponseUrl.replaceAll("static[-]files[/]....", "static-files/aaaa");
            Page page = webClient.getPage(modifiedUrl);
            assertEquals(404, page.getWebResponse().getStatusCode(), "resource not found");
            assertThat("resource not found", page.getWebResponse().getContentAsString(), containsString(ResourceDomainFilter.ERROR_RESPONSE));
        }


    }

    @Test
    void missingPermissionsCause403() throws Exception {
        // setup: A job that creates a file in its workspace
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(new CreateFileBuilder("file.html", "<html><body>the content</body></html>"));
        project.save();

        // setup: Everyone has permission to Jenkins and the job
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy a = new MockAuthorizationStrategy();
        a.grant(Jenkins.READ).everywhere().toEveryone();
        a.grant(Item.READ, Item.WORKSPACE).onItems(project).toEveryone();
        j.jenkins.setAuthorizationStrategy(a);

        j.buildAndAssertSuccess(project);

        JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.setThrowExceptionOnFailingStatusCode(false);
        webClient.setRedirectEnabled(true);

        // basics work
        HtmlPage page = webClient.getPage(project, "ws/file.html");
        assertEquals(200, page.getWebResponse().getStatusCode(), "page is found");
        assertTrue(page.getWebResponse().getContentAsString().contains("the content"), "page content is as expected");

        URL anonUrl = page.getUrl();
        assertTrue(anonUrl.toString().contains("/static-files/"), "page is served by resource domain");

        // now remove workspace permission from all users
        a = new MockAuthorizationStrategy();
        a.grant(Jenkins.READ).everywhere().toEveryone();
        a.grant(Item.READ).onItems(project).toEveryone();
        j.jenkins.setAuthorizationStrategy(a);

        // and we get a 403 response
        page = webClient.getPage(anonUrl);
        assertEquals(403, page.getWebResponse().getStatusCode(), "page is not found");
        assertThat("Response mentions workspace permission", page.getWebResponse().getContentAsString(), containsString("Failed permission check: anonymous is missing the Job/Workspace permission"));

        // now remove Job/Read permission from all users (but grant Discover)
        a = new MockAuthorizationStrategy();
        a.grant(Jenkins.READ).everywhere().toEveryone();
        a.grant(Item.DISCOVER).onItems(project).toEveryone();
        j.jenkins.setAuthorizationStrategy(a);

        // and we get a 403 response asking to log in (Job/Discover is basically meant to be granted to anonymous only)
        page = webClient.getPage(anonUrl);
        assertEquals(403, page.getWebResponse().getStatusCode(), "page is not found");
        assertThat("Response mentions workspace permission", page.getWebResponse().getContentAsString(), containsString("Failed permission check: Please login to access job"));
    }

    @Test
    void projectWasRenamedCauses404() throws Exception {
        // setup: A job that creates a file in its workspace
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(new CreateFileBuilder("file.html", "<html><body>the content</body></html>"));
        project.save();

        // setup: Everyone has permission to Jenkins and the job
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy a = new MockAuthorizationStrategy();
        a.grant(Jenkins.READ, Item.READ, Item.WORKSPACE).everywhere().toEveryone();
        j.jenkins.setAuthorizationStrategy(a);

        j.buildAndAssertSuccess(project);

        JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.setThrowExceptionOnFailingStatusCode(false);
        webClient.setRedirectEnabled(true);

        HtmlPage page = webClient.getPage(project, "ws/file.html");
        assertEquals(200, page.getWebResponse().getStatusCode(), "page is found");
        assertTrue(page.getWebResponse().getContentAsString().contains("the content"), "page content is as expected");

        URL url = page.getUrl();
        assertTrue(url.toString().contains("/static-files/"), "page is served by resource domain");

        project.renameTo("new-job-name"); // or delete, doesn't really matter

        Page failedPage = webClient.getPage(url);
        assertEquals(404, failedPage.getWebResponse().getStatusCode(), "page is not found");
        assertEquals("Not Found", failedPage.getWebResponse().getStatusMessage(), "page is not found"); // TODO Is this not done through our exception handler?
    }

//    @Test
    public void indexFileIsUsedIfDefined() {
        // TODO Test with DBS with and without directory index file
    }

    @Test
    void adminMonitorShowsUpWithOverriddenCSP() {
        ResourceDomainRecommendation monitor = ExtensionList.lookupSingleton(ResourceDomainRecommendation.class);
        assertFalse(monitor.isActivated());
        System.setProperty(DirectoryBrowserSupport.class.getName() + ".CSP", "");
        try {
            assertFalse(monitor.isActivated());
            ResourceDomainConfiguration.get().setUrl(null);
            assertTrue(monitor.isActivated());
        } finally {
            System.clearProperty(DirectoryBrowserSupport.class.getName() + ".CSP");
        }
        assertFalse(monitor.isActivated());
    }

    @Test
    void testColonUserName() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy a = new MockAuthorizationStrategy();
        a.grant(Jenkins.READ).everywhere().toEveryone();
        j.jenkins.setAuthorizationStrategy(a);

        JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.setRedirectEnabled(true);
        webClient.login("foo:bar");

        Page page = webClient.goTo("userContent/readme.txt", "text/plain");
        String resourceResponseUrl = page.getUrl().toString();
        assertEquals(200, page.getWebResponse().getStatusCode(), "resource response success");
        assertNull(page.getWebResponse().getResponseHeaderValue("Content-Security-Policy"), "no CSP headers");
        assertTrue(resourceResponseUrl.contains(RESOURCE_DOMAIN), "Served from resource domain");
        assertTrue(resourceResponseUrl.contains("static-files"), "Served from resource action");
    }

    @Test
    void testRedirectUrls() {
        ResourceDomainRootAction rootAction = ResourceDomainRootAction.get();
        String url = rootAction.getRedirectUrl(new ResourceDomainRootAction.Token("foo", "bar", Instant.now()), "foo bar baz");
        assertFalse(url.contains(" "), "urlencoded");
    }

    @Test
    @Issue("JENKINS-59849")
    void testUrlEncoding() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(new CreateFileBuilder("This has spaces and is 100% evil.html", "<html><body>the content</body></html>"));
        project.save();

        j.buildAndAssertSuccess(project);

        JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.setThrowExceptionOnFailingStatusCode(false);
        webClient.setRedirectEnabled(true);

        HtmlPage page = webClient.getPage(project, "ws/This%20has%20spaces%20and%20is%20100%25%20evil.html");
        assertEquals(200, page.getWebResponse().getStatusCode(), "page is found");
        assertTrue(page.getWebResponse().getContentAsString().contains("the content"), "page content is as expected");

        URL url = page.getUrl();
        assertTrue(url.toString().contains("/static-files/"), "page is served by resource domain");
    }

    @Test
    @Issue("JENKINS-59849")
    void testMoreUrlEncoding() throws Exception {
        assumeFalse(Functions.isWindows(), "TODO: Implement this test on Windows");
        JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.setThrowExceptionOnFailingStatusCode(false);
        webClient.setRedirectEnabled(true);

        Page page = webClient.goTo("100%25%20evil/%20100%25%20evil%20dir%20name%20%20%20/%20100%25%20evil%20content%20.html");
        assertEquals(200, page.getWebResponse().getStatusCode(), "page is found");
        assertTrue(page.getWebResponse().getContentAsString().contains("this is the content"), "page content is as expected");

        URL url = page.getUrl();
        assertTrue(url.toString().contains("/static-files/"), "page is served by resource domain");

        URL dirUrl = new URI(url.toString().replace("%20100%25%20evil%20content%20.html", "")).toURL();
        Page dirPage = webClient.getPage(dirUrl);
        assertEquals(200, dirPage.getWebResponse().getStatusCode(), "page is found");
        assertTrue(dirPage.getWebResponse().getContentAsString().contains("href"), "page content is HTML");
        assertTrue(dirPage.getWebResponse().getContentAsString().contains("evil content"), "page content references file");

        URL topDirUrl = new URI(url.toString().replace("%20100%25%20evil%20dir%20name%20%20%20/%20100%25%20evil%20content%20.html", "")).toURL();
        Page topDirPage = webClient.getPage(topDirUrl);
        assertEquals(200, topDirPage.getWebResponse().getStatusCode(), "page is found");
        assertTrue(topDirPage.getWebResponse().getContentAsString().contains("href"), "page content is HTML");
        assertTrue(topDirPage.getWebResponse().getContentAsString().contains("evil dir name"), "page content references directory");
    }

    @TestExtension
    public static class RootActionImpl implements UnprotectedRootAction {

        @CheckForNull
        @Override
        public String getIconFileName() {
            return null;
        }

        @CheckForNull
        @Override
        public String getDisplayName() {
            return null;
        }

        @CheckForNull
        @Override
        public String getUrlName() {
            return "100% evil";
        }

        public HttpResponse doDynamic() throws Exception {
            Jenkins jenkins = Jenkins.get();
            FilePath tempDir = jenkins.getRootPath().createTempDir("root", "tmp");
            tempDir.child(" 100% evil dir name   ").child(" 100% evil content .html").write("this is the content", "UTF-8");
            return new DirectoryBrowserSupport(jenkins, tempDir, "title", "", true);
        }
    }

    @Test
    void authenticatedCannotAccessResourceDomainUnlessAllowedBySystemProperty() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        final MockAuthorizationStrategy authorizationStrategy = new MockAuthorizationStrategy();
        authorizationStrategy.grant(Jenkins.ADMINISTER).everywhere().to("admin").grant(Jenkins.READ).everywhere().toEveryone();
        j.jenkins.setAuthorizationStrategy(authorizationStrategy);
        final String resourceUrl;
        try (JenkinsRule.WebClient wc = j.createWebClient().withRedirectEnabled(false).withThrowExceptionOnFailingStatusCode(false)) {
            final Page htmlPage = wc.goTo("userContent/readme.txt", "");
            resourceUrl = htmlPage.getWebResponse().getResponseHeaderValue("Location");
        }
        assertThat(resourceUrl, containsString("static-files/"));
        try (JenkinsRule.WebClient wc = j.createWebClient().withBasicApiToken("admin")) {
            assertThat(assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(new URL(resourceUrl))).getStatusCode(), is(400));
        }
        try (JenkinsRule.WebClient wc = j.createWebClient().withBasicCredentials("admin")) {
            assertThat(assertThrows(FailingHttpStatusCodeException.class, () -> wc.getPage(new URL(resourceUrl))).getStatusCode(), is(400));
        }

        ResourceDomainRootAction.ALLOW_AUTHENTICATED_USER = true;
        try (JenkinsRule.WebClient wc = j.createWebClient().withBasicApiToken("admin")) {
            assertThat(wc.getPage(new URL(resourceUrl)).getWebResponse().getStatusCode(), is(200));
        }
        try (JenkinsRule.WebClient wc = j.createWebClient().withBasicCredentials("admin")) {
            assertThat(wc.getPage(new URL(resourceUrl)).getWebResponse().getStatusCode(), is(200));
        }
    }
}
