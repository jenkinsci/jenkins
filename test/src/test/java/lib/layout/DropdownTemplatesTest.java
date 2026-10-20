package lib.layout;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Actionable;
import hudson.model.UnprotectedRootAction;
import hudson.util.HttpResponses;
import jenkins.model.ModelObjectWithContextMenu;
import org.htmlunit.Page;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.DomNodeList;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlElementUtil;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.WebMethod;

@WithJenkins
class DropdownTemplatesTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void testConfirmationPostActionUrls() throws Exception {
        TestRootAction rootAction = j.jenkins.getExtensionList(UnprotectedRootAction.class).get(TestRootAction.class);
        assertNotNull(rootAction);

        try (JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false)) {
            // Relative postTo
            HtmlPage page = wc.goTo("dropdown-templates-test");
            HtmlElement breadcrumbItem = page.getDocumentElement()
                    .getOneHtmlElementByAttribute("li", "data-has-menu", "true");
            assertNotNull(breadcrumbItem, "Breadcrumb menu item should exist");
            HtmlElementUtil.click(breadcrumbItem);
            wc.waitForBackgroundJavaScript(2000);

            Page afterRelative = clickTaskAndConfirm(page, wc, "Relative Action");
            String relativeBody = afterRelative.getWebResponse().getContentAsString();
            assertTrue(relativeBody.contains("param1=val1&param2=val2"), "got: " + relativeBody);

            // Root-relative postTo — reload for a clean state
            page = wc.goTo("dropdown-templates-test");
            breadcrumbItem = page.getDocumentElement()
                    .getOneHtmlElementByAttribute("li", "data-has-menu", "true");
            HtmlElementUtil.click(breadcrumbItem);
            wc.waitForBackgroundJavaScript(2000);

            Page afterRoot = clickTaskAndConfirm(page, wc, "Root Relative Action");
            String rootBody = afterRoot.getWebResponse().getContentAsString();
            assertTrue(rootBody.contains("root:param1=val1&param2=val2"), "got: " + rootBody);
        }
    }

    private Page clickTaskAndConfirm(HtmlPage page, JenkinsRule.WebClient wc, String taskTitle) throws Exception {
        HtmlElement item = findTaskByText(page, taskTitle);

        assertNotNull(item, "Task '" + taskTitle + "' should exist in dropdown");

        HtmlElementUtil.click(item);
        wc.waitForBackgroundJavaScript(2000);

        HtmlButton confirmButton = page.getDocumentElement()
                .getOneHtmlElementByAttribute("button", "data-id", "ok");
        assertNotNull(confirmButton, "Confirm dialog button should exist");

        return HtmlElementUtil.click(confirmButton);
    }

    private HtmlElement findTaskByText(HtmlPage page, String text) {
        DomNodeList<DomElement> candidates = page.getElementsByTagName("a");
        for (DomElement el : candidates) {
            if (el.getTextContent() != null && el.getTextContent().trim().contains(text)) {
                return (HtmlElement) el;
            }
        }
        return null;
    }

    @TestExtension
    public static final class TestRootAction extends Actionable
            implements UnprotectedRootAction, ModelObjectWithContextMenu {

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return "Dropdown Templates Test";
        }

        @Override
        public String getUrlName() {
            return "dropdown-templates-test";
        }

        @Override
        public String getSearchUrl() {
            return getUrlName();
        }

        @Override
        public ContextMenu doContextMenu(StaplerRequest2 request, StaplerResponse2 response) throws Exception {
            return new ContextMenu().from(this, request, response);
        }

        @WebMethod(name = "doPostAction")
        public HttpResponse doPostAction(StaplerRequest2 req) {
            return HttpResponses.plainText("received:param1=" + req.getParameter("param1") + "&param2=" + req.getParameter("param2"));
        }

        @WebMethod(name = "doRootAction")
        public HttpResponse doRootAction(StaplerRequest2 req) {
            return HttpResponses.plainText("root:param1=" + req.getParameter("param1") + "&param2=" + req.getParameter("param2"));
        }
    }
}
