package jenkins.security;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;

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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.CreateFileBuilder;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.HttpResponse;

@Issue("JENKINS-41891")
@For({ ResourceDomainRootAction.class, ResourceDomainFilter.class, ResourceDomainConfiguration.class })
public class ResourceDomainTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private static final String RESOURCE_DOMAIN = "127.0.0.1";

    @Before
    public void prepare() throws Exception {
        String resourceRoot;
        URL root = j.getURL(); // which always will use "localhost", see JenkinsRule#getURL()
        Assert.assertTrue(root.toString().contains("localhost")); // to be safe

        resourceRoot = root.toString().replace("localhost", RESOURCE_DOMAIN);
        ResourceDomainConfiguration configuration = ExtensionList.lookupSingleton(ResourceDomainConfiguration.class);
        configuration.setUrl(resourceRoot);
    }

    @Test
    public void secondDomainBasics() throws Exception {
        JenkinsRule.WebClient webClient = j.createWebClient();

        { // DBS directory listing is shown as always
            Page page = webClient.goTo("userContent");
            Assert.assertEquals("successful request", 200, page.getWebResponse().getStatusCode());
            Assert.assertTrue("still on the original URL", page.getUrl().toString().contains("/userContent"));
            Assert.assertTrue("web page", page.isHtmlPage());
            Assert.assertTrue("complex web page", page.getWebResponse().getContentAsString().contains("javascript"));
        }

        String resourceResponseUrl;
        { // DBS on primary domain forwards to second domain when trying to access a file URL
            webClient.setRedirectEnabled(true);
            Page page = webClient.goTo("userContent/readme.txt", "text/plain");
            resourceResponseUrl = page.getUrl().toString();
            Assert.assertEquals("resource response success", 200, page.getWebResponse().getStatusCode());
            Assert.assertNull("no CSP headers", page.getWebResponse().getResponseHeaderValue("Content-Security-Policy"));
            Assert.assertTrue("Served from resource domain", resourceResponseUrl.contains(RESOURCE_DOMAIN));
            Assert.assertTrue("Served from resource action", resourceResponseUrl.contains("static-files"));
        }

        { // direct access to resource URL works
            Page page = webClient.getPage(resourceResponseUrl);
            resourceResponseUrl = page.getUrl().toString();
            Assert.assertEquals("resource response success", 200, page.getWebResponse().getStatusCode());
            Assert.assertNull("no CSP headers", page.getWebResponse().getResponseHeaderValue("Content-Security-Policy"));
            Assert.assertTrue("Served from resource domain", resourceResponseUrl.contains(RESOURCE_DOMAIN));
            Assert.assertTrue("Served from resource action", resourceResponseUrl.contains("static-files"));
        }

        { // show directory index
            webClient.setRedirectEnabled(false);
            webClient.setThrowExceptionOnFailingStatusCode(false);
            Page page = webClient.getPage(resourceResponseUrl.replace("readme.txt", ""));
            Assert.assertEquals("directory listing response", 200, page.getWebResponse().getStatusCode());
            String responseContent = page.getWebResponse().getContentAsString();
            Assert.assertTrue("directory listing shown", responseContent.contains("readme.txt"));
            Assert.assertTrue("is HTML", responseContent.contains("href="));
        }

        String resourceRootUrl = ResourceDomainConfiguration.get().getUrl();
        {
            webClient.setThrowExceptionOnFailingStatusCode(false);
            Page page = webClient.getPage(resourceRootUrl);
            Assert.assertEquals("resource root URL response is 404", 404, page.getWebResponse().getStatusCode());
        }

        {
            webClient.setThrowExceptionOnFailingStatusCode(false);
            Page page = webClient.getPage(resourceRootUrl + "/static-files/");
            Assert.assertEquals("resource action index page response is 404", 404, page.getWebResponse().getStatusCode());
        }

        { // second domain invalid URL gets 404
            webClient.setThrowExceptionOnFailingStatusCode(false);
            String uuid = UUID.randomUUID().toString();
            Page page = webClient.getPage(resourceRootUrl + "static-files/" + uuid);
            Assert.assertEquals("resource response is 404", 404, page.getWebResponse().getStatusCode());
            Assert.assertTrue("response URL is still the same", page.getUrl().toString().contains(uuid));
        }

        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy a = new MockAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(a);

        { // fails without Overall/Read
            webClient.withRedirectEnabled(false).withThrowExceptionOnFailingStatusCode(false);
            Page page = webClient.getPage(resourceResponseUrl);
            resourceResponseUrl = page.getUrl().toString();
            Assert.assertEquals("resource response failed", 403, page.getWebResponse().getStatusCode());
            Assert.assertNull("no CSP headers", page.getWebResponse().getResponseHeaderValue("Content-Security-Policy"));
            Assert.assertTrue("Served from resource domain", resourceResponseUrl.contains(RESOURCE_DOMAIN));
        }

        a.grant(Jenkins.READ).onRoot().to("anonymous");

        { // now it works again
            Page page = webClient.getPage(resourceResponseUrl);
            resourceResponseUrl = page.getUrl().toString();
            Assert.assertEquals("resource response success", 200, page.getWebResponse().getStatusCode());
            Assert.assertNull("no CSP headers", page.getWebResponse().getResponseHeaderValue("Content-Security-Policy"));
            Assert.assertTrue("Served from resource domain", resourceResponseUrl.contains(RESOURCE_DOMAIN));
            Assert.assertTrue("Served from resource action", resourceResponseUrl.contains("static-files"));
        }
    }

    @Test
    public void clearRootUrl() throws Exception {
        JenkinsLocationConfiguration.get().setUrl(null);

        JenkinsRule.WebClient webClient = j.createWebClient();

        String resourceResponseUrl;
        {
            webClient.setRedirectEnabled(true);
            Page page = webClient.goTo("userContent/readme.txt", "text/plain");
            resourceResponseUrl = page.getUrl().toString();
            Assert.assertEquals("resource response success", 200, page.getWebResponse().getStatusCode());
            Assert.assertNotNull("CSP headers set", page.getWebResponse().getResponseHeaderValue("Content-Security-Policy"));
            Assert.assertFalse("Not served from resource domain", resourceResponseUrl.contains(RESOURCE_DOMAIN));
            Assert.assertFalse("Not served from resource action", resourceResponseUrl.contains("static-files"));
            Assert.assertTrue("Original URL", resourceResponseUrl.contains("userContent/readme.txt"));
        }

    }

    @Test
    public void secondDomainCannotBeFaked() throws Exception {
        JenkinsRule.WebClient webClient = j.createWebClient();

        String resourceResponseUrl;
        { // first, obtain a resource response URL
            webClient.setRedirectEnabled(true);
            webClient.setThrowExceptionOnFailingStatusCode(false);
            Page page = webClient.goTo("userContent/readme.txt", "text/plain");
            resourceResponseUrl = page.getUrl().toString();
            Assert.assertEquals("resource response success", 200, page.getWebResponse().getStatusCode());
            Assert.assertNull("no CSP headers", page.getWebResponse().getResponseHeaderValue("Content-Security-Policy"));
            Assert.assertTrue("Served from resource domain", resourceResponseUrl.contains(RESOURCE_DOMAIN));
            Assert.assertTrue("Served from resource action", resourceResponseUrl.contains("static-files"));
        }

        {
            // now, modify its prefix to have an invalid HMAC
            String modifiedUrl = resourceResponseUrl.replaceAll("static[-]files[/]....", "static-files/aaaa");
            Page page = webClient.getPage(modifiedUrl);
            Assert.assertEquals("resource not found", 404, page.getWebResponse().getStatusCode());
            assertThat("resource not found", page.getWebResponse().getContentAsString(), containsString(ResourceDomainFilter.ERROR_RESPONSE));
        }


    }

    @Test
    public void missingPermissionsCause403() throws Exception {
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
        Assert.assertEquals("page is found", 200, page.getWebResponse().getStatusCode());
        Assert.assertTrue("page content is as expected", page.getWebResponse().getContentAsString().contains("the content"));

        URL anonUrl = page.getUrl();
        Assert.assertTrue("page is served by resource domain", anonUrl.toString().contains("/static-files/"));

        // now remove workspace permission from all users
        a = new MockAuthorizationStrategy();
        a.grant(Jenkins.READ).everywhere().toEveryone();
        a.grant(Item.READ).onItems(project).toEveryone();
        j.jenkins.setAuthorizationStrategy(a);

        // and we get a 403 response
        page = webClient.getPage(anonUrl);
        Assert.assertEquals("page is not found", 403, page.getWebResponse().getStatusCode());
        assertThat("Response mentions workspace permission", page.getWebResponse().getContentAsString(), containsString("Failed permission check: anonymous is missing the Job/Workspace permission"));

        // now remove Job/Read permission from all users (but grant Discover)
        a = new MockAuthorizationStrategy();
        a.grant(Jenkins.READ).everywhere().toEveryone();
        a.grant(Item.DISCOVER).onItems(project).toEveryone();
        j.jenkins.setAuthorizationStrategy(a);

        // and we get a 403 response asking to log in (Job/Discover is basically meant to be granted to anonymous only)
        page = webClient.getPage(anonUrl);
        Assert.assertEquals("page is not found", 403, page.getWebResponse().getStatusCode());
        assertThat("Response mentions workspace permission", page.getWebResponse().getContentAsString(), containsString("Failed permission check: Please login to access job"));
    }

    @Test
    public void projectWasRenamedCauses404() throws Exception {
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
        Assert.assertEquals("page is found", 200, page.getWebResponse().getStatusCode());
        Assert.assertTrue("page content is as expected", page.getWebResponse().getContentAsString().contains("the content"));

        URL url = page.getUrl();
        Assert.assertTrue("page is served by resource domain", url.toString().contains("/static-files/"));

        project.renameTo("new-job-name"); // or delete, doesn't really matter

        Page failedPage = webClient.getPage(url);
        Assert.assertEquals("page is not found", 404, failedPage.getWebResponse().getStatusCode());
        Assert.assertEquals("page is not found", "Not Found", failedPage.getWebResponse().getStatusMessage()); // TODO Is this not done through our exception handler?
    }

//    @Test
    public void indexFileIsUsedIfDefined() {
        // TODO Test with DBS with and without directory index file
    }

    @Test
    public void adminMonitorShowsUpWithOverriddenCSP() {
        ResourceDomainRecommendation monitor = ExtensionList.lookupSingleton(ResourceDomainRecommendation.class);
        Assert.assertFalse(monitor.isActivated());
        System.setProperty(DirectoryBrowserSupport.class.getName() + ".CSP", "");
        try {
            Assert.assertFalse(monitor.isActivated());
            ResourceDomainConfiguration.get().setUrl(null);
            Assert.assertTrue(monitor.isActivated());
        } finally {
            System.clearProperty(DirectoryBrowserSupport.class.getName() + ".CSP");
        }
        Assert.assertFalse(monitor.isActivated());
    }

    @Test
    public void testColonUserName() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy a = new MockAuthorizationStrategy();
        a.grant(Jenkins.READ).everywhere().toEveryone();
        j.jenkins.setAuthorizationStrategy(a);

        JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.setRedirectEnabled(true);
        webClient.login("foo:bar");

        Page page = webClient.goTo("userContent/readme.txt", "text/plain");
        String resourceResponseUrl = page.getUrl().toString();
        Assert.assertEquals("resource response success", 200, page.getWebResponse().getStatusCode());
        Assert.assertNull("no CSP headers", page.getWebResponse().getResponseHeaderValue("Content-Security-Policy"));
        Assert.assertTrue("Served from resource domain", resourceResponseUrl.contains(RESOURCE_DOMAIN));
        Assert.assertTrue("Served from resource action", resourceResponseUrl.contains("static-files"));
    }

    @Test
    public void testRedirectUrls() {
        ResourceDomainRootAction rootAction = ResourceDomainRootAction.get();
        String url = rootAction.getRedirectUrl(new ResourceDomainRootAction.Token("foo", "bar", Instant.now()), "foo bar baz");
        Assert.assertFalse("urlencoded", url.contains(" "));
    }

    @Test
    @Issue("JENKINS-59849")
    public void testUrlEncoding() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(new CreateFileBuilder("This has spaces and is 100% evil.html", "<html><body>the content</body></html>"));
        project.save();

        j.buildAndAssertSuccess(project);

        JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.setThrowExceptionOnFailingStatusCode(false);
        webClient.setRedirectEnabled(true);

        HtmlPage page = webClient.getPage(project, "ws/This%20has%20spaces%20and%20is%20100%25%20evil.html");
        Assert.assertEquals("page is found", 200, page.getWebResponse().getStatusCode());
        Assert.assertTrue("page content is as expected", page.getWebResponse().getContentAsString().contains("the content"));

        URL url = page.getUrl();
        Assert.assertTrue("page is served by resource domain", url.toString().contains("/static-files/"));
    }

    @Test
    @Issue("JENKINS-59849")
    public void testMoreUrlEncoding() throws Exception {
        assumeFalse("TODO: Implement this test on Windows", Functions.isWindows());
        JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.setThrowExceptionOnFailingStatusCode(false);
        webClient.setRedirectEnabled(true);

        Page page = webClient.goTo("100%25%20evil/%20100%25%20evil%20dir%20name%20%20%20/%20100%25%20evil%20content%20.html");
        Assert.assertEquals("page is found", 200, page.getWebResponse().getStatusCode());
        Assert.assertTrue("page content is as expected", page.getWebResponse().getContentAsString().contains("this is the content"));

        URL url = page.getUrl();
        Assert.assertTrue("page is served by resource domain", url.toString().contains("/static-files/"));

        URL dirUrl = new URI(url.toString().replace("%20100%25%20evil%20content%20.html", "")).toURL();
        Page dirPage = webClient.getPage(dirUrl);
        Assert.assertEquals("page is found", 200, dirPage.getWebResponse().getStatusCode());
        Assert.assertTrue("page content is HTML", dirPage.getWebResponse().getContentAsString().contains("href"));
        Assert.assertTrue("page content references file", dirPage.getWebResponse().getContentAsString().contains("evil content"));

        URL topDirUrl = new URI(url.toString().replace("%20100%25%20evil%20dir%20name%20%20%20/%20100%25%20evil%20content%20.html", "")).toURL();
        Page topDirPage = webClient.getPage(topDirUrl);
        Assert.assertEquals("page is found", 200, topDirPage.getWebResponse().getStatusCode());
        Assert.assertTrue("page content is HTML", topDirPage.getWebResponse().getContentAsString().contains("href"));
        Assert.assertTrue("page content references directory", topDirPage.getWebResponse().getContentAsString().contains("evil dir name"));
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
    public void authenticatedCannotAccessResourceDomainUnlessAllowedBySystemProperty() throws Exception {
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
