package hudson.widgets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.slaves.DumbSlave;
import hudson.tasks.BuildStepDescriptor;
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
import org.jvnet.hudson.test.TestExtension;
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

        addBuildStep(page, "Execute shell");
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
    void theStoredConfigurationOfJenkinsIsNotRendered() throws Exception {
        String page = "descriptorByName/jenkins.model.GlobalNodePropertiesConfiguration/page/heteroList";
        assertEquals(404, render(j.createWebClient().login("configurer"), "job/p/" + page));
        assertEquals(404, render(j.createWebClient().login("reader"), "user/reader/" + page));
    }

    @Test
    void aUserNeedsAdministerToRenderOnThemselves() throws Exception {
        String page = "descriptorByName/hudson.tasks.Shell/page/heteroList";
        assertEquals(403, render(j.createWebClient().login("reader"), "user/reader/" + page));
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("admin"));
        assertEquals(200, render(j.createWebClient().login("admin"), "user/admin/" + page));
    }

    @Test
    void anAdjunctNotOnThePageSetsUpEveryStepUsingIt() throws Exception {
        HtmlPage page = j.createWebClient().login("configurer").getPage(p, "configure");
        assertThat(proxyUrl(page, WithAdjunct.class.getName()), endsWith("/page/heteroList"));
        addBuildStep(page, "With adjunct");
        addBuildStep(page, "With adjunct");
        page.getWebClient().waitForBackgroundJavaScript(5000);
        assertEquals(2, page.querySelectorAll(".descriptor-page-test").size());
        for (var e : page.querySelectorAll(".descriptor-page-test")) {
            assertEquals("ready", e.getTextContent(), "the adjunct ran before the step was set up");
        }
    }

    /**
     * Scripts inserted with content are not run by browsers, so an adjunct inserted, but not run, is not on the page;
     * and content rendered while the adjunct loads for other content waits for it.
     * (HtmlUnit runs inserted scripts, so the build steps above cannot show either.)
     */
    @Test
    void anAdjunctRunsOnceBeforeAllTheContentUsingIt() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient().login("configurer");
        WebResponse step = renderResponse(wc, "job/p/descriptorByName/" + WithAdjunct.class.getName() + "/page/heteroList");
        Matcher m = Pattern.compile("<script[^>]*src=['\"]([^'\"]*DescriptorPageTest/adjunct\\.js)['\"]").matcher(step.getContentAsString());
        assertTrue(m.find(), "the step includes its adjunct");
        HtmlPage page = wc.getPage(p, "configure");
        page.executeJavaScript("const s = document.createElement('script');"
                + "s.type = 'text/x-inserted';"
                + "s.src = '" + m.group(1) + "';"
                + "document.body.appendChild(s);"
                + "window.runsBefore = [];"
                + "for (let i = 0; i < 2; i++) {"
                + "  evalInnerHtmlScripts('<script src=\"" + m.group(1) + "\"></script>', () => runsBefore.push(window.descriptorPageTestAdjunctRuns));"
                + "}");
        page.getWebClient().waitForBackgroundJavaScript(5000);
        assertEquals("[1,1]", page.executeJavaScript("JSON.stringify(window.runsBefore)").getJavaScriptResult());
    }

    public static class WithAdjunct extends Builder {
        @TestExtension({"anAdjunctNotOnThePageSetsUpEveryStepUsingIt", "anAdjunctRunsOnceBeforeAllTheContentUsingIt"})
        public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
            @Override
            public boolean isApplicable(Class<? extends AbstractProject> jobType) {
                return true;
            }

            @Override
            public String getDisplayName() {
                return "With adjunct";
            }
        }
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

    /** Adds a build step as the user does, without waiting for it to render. */
    private static void addBuildStep(HtmlPage page, String displayName) throws Exception {
        ((HtmlElement) page.querySelector("button.hetero-list-add[suffix='builder']")).click();
        page.getWebClient().waitForBackgroundJavaScript(2000);
        HtmlElement item = page.querySelectorAll(".jenkins-dropdown__item").stream().map(HtmlElement.class::cast)
                .filter(e -> e.getTextContent().contains(displayName)).findFirst().orElseThrow();
        item.click();
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
