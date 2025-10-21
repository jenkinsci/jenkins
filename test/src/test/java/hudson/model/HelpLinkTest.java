package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.matrix.MatrixProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import java.util.List;
import org.htmlunit.WebResponseListener;
import org.htmlunit.html.DomNodeUtil;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlElementUtil;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Click all the help links and make sure they resolve to some text, not 404.
 *
 * @author Kohsuke Kawaguchi
 */
@Disabled
/*
    Excluding test to be able to ship 2.0 beta 1
    Jenkins confirms that this test is now taking 45mins to complete.

    The problem appears to be the following. When a help link is clicked, the execution hangs at the following point:

    "Executing negative(hudson.model.HelpLinkTest)@1" prio=5 tid=0x1 nid=NA waiting
      java.lang.Thread.State: WAITING
          at java.lang.Object.wait(Object.java:-1)
          at org.htmlunit.javascript.background.JavaScriptJobManagerImpl.waitForJobs(JavaScriptJobManagerImpl.java:200)
          at org.htmlunit.WebClient.waitForBackgroundJavaScript(WebClient.java:1843)
          at org.htmlunit.WebClientUtil.waitForJSExec(WebClientUtil.java:57)
          at org.htmlunit.WebClientUtil.waitForJSExec(WebClientUtil.java:46)
          at org.htmlunit.html.HtmlElementUtil.click(HtmlElementUtil.java:61)
          at hudson.model.HelpLinkTest.clickAllHelpLinks(HelpLinkTest.java:70)
          at hudson.model.HelpLinkTest.clickAllHelpLinks(HelpLinkTest.java:61)
          at hudson.model.HelpLinkTest.negative(HelpLinkTest.java:106)

    In debugger, I can see that JavaScriptJobManagerImpl.waitForJobs is looping through yet each time getJobCount()>0
    because there's always some window.setTimeout activities that appear to be scheduled. Common ones are:

        window.setTimeout(  function () {
              thisConfig.trackSectionVisibility();
          }, 500)

        window.setTimeout(  function () {
              return __method.apply(__method, args);
          }, 10)

        window.setTimeout(  function () {
              oSelf._printBuffer();
          }, 100)

    getJobCount() never appears to return 0, so the method blocks until the time out of 10 secs is reached.
    Multiply that by 50 or so help links to click and now you see why each test takes 10mins.

    Maybe this is related to the scrollspy changes?
 */
@WithJenkins
class HelpLinkTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void systemConfig() throws Exception {
        clickAllHelpLinks(j.createWebClient().goTo("configure"));
    }

    @Test
    void freestyleConfig() throws Exception {
        clickAllHelpLinks(j.createFreeStyleProject());
    }

    @Test
    void matrixConfig() throws Exception {
        clickAllHelpLinks(j.jenkins.createProject(MatrixProject.class, "mp"));
    }

    private void clickAllHelpLinks(AbstractProject p) throws Exception {
        // TODO: how do we add all the builders and publishers so that we can test this meaningfully?
        clickAllHelpLinks(j.createWebClient(), p);
    }

    private void clickAllHelpLinks(JenkinsRule.WebClient webClient, AbstractProject p) throws Exception {
        // TODO: how do we add all the builders and publishers so that we can test this meaningfully?
        clickAllHelpLinks(webClient.getPage(p, "configure"));
    }

    private void clickAllHelpLinks(HtmlPage p) throws Exception {
        List<?> helpLinks = DomNodeUtil.selectNodes(p, "//a[@class='jenkins-help-button']");
        assertThat(helpLinks, not(empty()));
        System.out.println("Clicking " + helpLinks.size() + " help links");

        for (HtmlAnchor helpLink : (List<HtmlAnchor>) helpLinks) {
            HtmlElementUtil.click(helpLink);
        }
    }

    public static class HelpNotFoundBuilder extends Publisher {
        public static final class DescriptorImpl extends BuildStepDescriptor {
            @Override
            public boolean isApplicable(Class jobType) {
                return true;
            }

            @Override
            public String getHelpFile() {
                return "no-such-file/exists";
            }
        }

        @Override
        public BuildStepMonitor getRequiredMonitorService() {
            return BuildStepMonitor.BUILD;
        }
    }

    /**
     * Make sure that this test is meaningful.
     * Intentionally put 404 and verify that it's detected.
     */
    @Test
    void negative() throws Exception {
        HelpNotFoundBuilder.DescriptorImpl d = new HelpNotFoundBuilder.DescriptorImpl();
        Publisher.all().add(d);
        try {
            FreeStyleProject p = j.createFreeStyleProject();
            p.getPublishersList().add(new HelpNotFoundBuilder());
            JenkinsRule.WebClient webclient = j.createWebClient();
            WebResponseListener.StatusListener statusListener = new WebResponseListener.StatusListener(404);
            webclient.addWebResponseListener(statusListener);

            clickAllHelpLinks(webclient, p);

            statusListener.assertHasResponses();
            String contentAsString = statusListener.getResponses().get(0).getContentAsString();
            assertTrue(contentAsString.contains(d.getHelpFile()));
        } finally {
            Publisher.all().remove(d);
        }
    }
}
