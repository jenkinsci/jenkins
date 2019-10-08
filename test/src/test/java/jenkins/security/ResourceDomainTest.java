package jenkins.security;

import com.gargoylesoftware.htmlunit.Page;
import hudson.ExtensionList;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import java.net.URL;
import java.util.UUID;

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

        String resourceRootUrl = ExtensionList.lookupSingleton(ResourceDomainConfiguration.class).getUrl();
        {
            webClient.setThrowExceptionOnFailingStatusCode(false);
            Page page = webClient.getPage(resourceRootUrl);
            Assert.assertEquals("resource root URL response is 404", 404, page.getWebResponse().getStatusCode());
        }

        {
            webClient.setThrowExceptionOnFailingStatusCode(false);
            Page page = webClient.getPage(resourceRootUrl + "/static-files");
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
            Assert.assertEquals("resource not found", ResourceDomainFilter.ERROR_RESPONSE, page.getWebResponse().getStatusMessage());
        }


    }

//    @Test
    public void missingPermissionsCause403() throws Exception {
        // TODO test handling of permissions issue when trying to route the internal request
    }

//    @Test
    public void projectWasRenamedCauses404() throws Exception {
        // TODO test handling of other exceptions (404 not found?) when trying to route the internal request
    }

//    @Test
    public void indexFileIsUsedIfDefined() throws Exception {
        // TODO Test with DBS with and without directory index file
    }
}
