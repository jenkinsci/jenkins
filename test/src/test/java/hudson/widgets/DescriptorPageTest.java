package hudson.widgets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.slaves.DumbSlave;
import hudson.tasks.Builder;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class DescriptorPageTest {

    private JenkinsRule j;
    private FreeStyleProject p;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        j = rule;
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Item.READ, Item.CONFIGURE, Computer.CONFIGURE).everywhere().to("configurer")
                .grant(Jenkins.READ, Item.READ).everywhere().to("reader"));
        p = j.createFreeStyleProject("p");
    }

    @Test
    void theBuildStepsOfAJobAreOfferedWithoutKeepingThemInTheSession() throws Exception {
        HtmlPage page = j.createWebClient().login("configurer").getPage(p, "configure");
        for (Descriptor<?> d : Builder.all()) {
            assertThat(proxyUrl(page, d.getId()), endsWith("/page/heteroList"));
        }
        assertThat(shellUrl(page), endsWith("/job/p/descriptorByName/hudson.tasks.Shell/page/heteroList"));

        ((HtmlElement) page.querySelector("button.hetero-list-add[suffix='builder']")).click();
        page.getWebClient().waitForBackgroundJavaScript(2000);
        HtmlElement item = page.querySelectorAll(".jenkins-dropdown__item").stream().map(HtmlElement.class::cast)
                .filter(e -> e.getTextContent().contains("Execute shell")).findFirst().orElseThrow();
        item.click();
        page.getWebClient().waitForBackgroundJavaScript(5000);
        assertNotNull(page.querySelector("div[descriptorId='hudson.tasks.Shell'] [name='command']"), "the build step is rendered");
    }

    @Test
    void onlyTheViewsOfThisClassAreRendered() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient().login("configurer");
        assertEquals(200, render(wc, "job/p/descriptorByName/hudson.tasks.Shell/page/heteroList"));
        assertEquals(200, render(wc, "job/p/descriptorByName/hudson.tasks.Shell/page/dropdownListBlock"));
        assertEquals(404, render(wc, "job/p/descriptorByName/hudson.tasks.Shell/page/index"));
        assertEquals(404, render(wc, "job/p/descriptorByName/hudson.tasks.Shell/page/heteroList.node"));
    }

    @Test
    void renderingForAnotherJobNeedsThePermissionToConfigureIt() throws Exception {
        j.createFreeStyleProject("q");
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Item.READ).everywhere().to("configurer")
                .grant(Item.CONFIGURE).onItems(p).to("configurer"));
        JenkinsRule.WebClient wc = j.createWebClient().login("configurer");
        assertEquals(200, render(wc, "job/p/descriptorByName/hudson.tasks.Shell/page/heteroList"));
        assertEquals(403, render(wc, "job/q/descriptorByName/hudson.tasks.Shell/page/heteroList"));
    }

    @Test
    void renderingNeedsThePermissionToConfigure() throws Exception {
        String url = shellUrl(j.createWebClient().login("configurer").getPage(p, "configure"));
        assertEquals(403, render(j.createWebClient().login("reader"), url));
    }

    @Test
    void anObjectThatDoesNotSayNeedsAdministerOnIt() throws Exception {
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ, Jenkins.MANAGE).everywhere().to("manager"));
        String page = "descriptorByName/hudson.tasks.Shell/page/heteroList";
        assertEquals(200, render(j.createWebClient().login("admin"), page));
        assertEquals(403, render(j.createWebClient().login("manager"), page));
    }

    @Test
    void aReadOnlyPageKeepsTheClosure() throws Exception {
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.READ, Item.READ, Item.EXTENDED_READ).everywhere().to("reader"));
        HtmlPage page = j.createWebClient().login("reader").getPage(p, "configure");
        assertThat(page.getWebResponse().getContentAsString(), not(containsString("/page/heteroList")));
    }

    @Test
    void theOptionsOfADropdownListAreOfferedWithoutKeepingThemInTheSession() throws Exception {
        DumbSlave agent = j.createSlave("agent", null, null);
        JenkinsRule.WebClient wc = j.createWebClient().login("configurer");
        HtmlPage page = wc.goTo("computer/agent/configure");
        Matcher m = Pattern.compile("data-proxy-url=\"([^\"]*RetentionStrategy\\$Demand[^\"]*)\"").matcher(page.getWebResponse().getContentAsString());
        assertTrue(m.find(), "on-demand availability is offered");
        String url = m.group(1);
        assertThat(url, endsWith("/computer/agent/descriptorByName/hudson.slaves.RetentionStrategy$Demand/page/dropdownListBlock.node"));
        WebResponse rsp = renderResponse(wc, url);
        assertEquals(200, rsp.getStatusCode());
        assertThat(rsp.getContentAsString(), containsString("inDemandDelay"));
    }

    /** The URL the page renders the Shell build step from; the page's script removes it from the page. */
    private static String shellUrl(HtmlPage page) {
        return proxyUrl(page, "hudson.tasks.Shell");
    }

    private static String proxyUrl(HtmlPage page, String descriptorId) {
        Matcher m = Pattern.compile("descriptorId=\"" + Pattern.quote(descriptorId) + "\".*?data-proxy-url=\"([^\"]+)\"", Pattern.DOTALL)
                .matcher(page.getWebResponse().getContentAsString());
        assertTrue(m.find(), descriptorId + " is offered");
        return m.group(1);
    }

    /** Renders as the page's script does; returns the status. */
    private int render(JenkinsRule.WebClient wc, String url) throws Exception {
        return renderResponse(wc, url).getStatusCode();
    }

    private WebResponse renderResponse(JenkinsRule.WebClient wc, String url) throws Exception {
        WebRequest request = new WebRequest(new URL(j.getURL(), url + "/render"), HttpMethod.POST);
        request.setAdditionalHeader("Content-Type", "application/x-stapler-method-invocation;charset=UTF-8");
        request.setAdditionalHeader("Crumb", "test");
        request.setRequestBody("[]");
        request.setAdditionalHeader("Jenkins-Crumb", "test");
        return wc.loadWebResponse(request);
    }
}
