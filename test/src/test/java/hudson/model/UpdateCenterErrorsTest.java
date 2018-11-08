package hudson.model;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.DomNodeList;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlElementUtil;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.Messages;
import hudson.PluginManager;
import hudson.util.HttpResponses;
import jenkins.model.Jenkins;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.xml.sax.SAXException;

import javax.annotation.CheckForNull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

public class UpdateCenterErrorsTest {

    private static final Logger LOGGER = Logger.getLogger(UpdateCenterErrorsTest.class.getName());

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule();

    /**
     * Check if the page contains the right message after checking an update site with an url that returns a 502 error code.
     * @throws Exception If there are errors managing the web elements.
     */
    @Test
    public void updateSiteReturn502Test() throws Exception {
        checkUpdateSite(Jenkins.getInstance().getRootUrl() + "updateSite502/getJson", "IOException: Server returned HTTP response code: 502 for URL", false);
    }

    /**
     * Check if the page contains the right message after checking an update site with an url that returns a wrong json.
     * @throws Exception If there are errors managing the web elements.
     */
    @Test
    public void updateSiteWrongJsonTest() throws Exception {
        checkUpdateSite(Jenkins.getInstance().getRootUrl() + "updateSiteWrongJson/getJson", "JSONException: Unquotted string 'wrongjson'", false);
    }

    /**
     * Check if the page contains the right message after checking an update site that returns a well defined json.
     * @throws Exception If there are errors managing the web elements.
     */
    @Test
    public void updateSiteRightJsonTest() throws Exception {
        // Save the actual value to leave it so, when the test finish, just in case it is needed for other tests
        boolean oldValueSignatureCheck = DownloadService.signatureCheck;
        try {
            //Avoid CertPathValidatorException: Algorithm constraints check failed on signature algorithm: MD5withRSA
            DownloadService.signatureCheck = false;
            // Have to end in update-center.json or it fails. See UpdateSite#getMetadataUrlForDownloadable
            checkUpdateSite(Jenkins.getInstance().getRootUrl() + "updateSiteRightJson/update-center.json", "", true );
        } finally {
            DownloadService.signatureCheck = oldValueSignatureCheck;
        }
    }

    /**
     * Checks if the message to activate the warnings is written in the log when the log level is higher than WARNING
     * and the attempts higher than 1.
     * @throws Exception See {@link #updateSiteWrongJsonTest()}
     */
    @Test
    public void changeLogLevelInLog() throws Exception {
        Logger.getLogger(PluginManager.class.getName()).setLevel(Level.SEVERE);
        PluginManager.checkUpdateAttempts = 2;
        updateSiteWrongJsonTest();

        Assert.assertTrue(logging.getMessages().stream().anyMatch(m -> m.contains(Messages.PluginManager_UpdateSiteChangeLogLevel())));

    }

    private HtmlButton getCheckNow(HtmlPage page){
        DomNodeList<HtmlElement> elements = page.getElementById("bottom-sticker").getElementsByTagName("button");
        assertEquals(1, elements.size());
        return (HtmlButton) elements.get(0);
    }

    /**
     * Check the update site.
     * @param urlUpdateSite If null, use the default update site, otherwise, use this update site.
     * @param message The message that should exist or not in the page after checking the update site.
     * @param isSuccess If true, test that PluginManager.CheckUpdateSesrverError + message doesn't exist in the page.
     *                  If false, test that PluginManager.CheckUpdateSesrverError + message exists in the page.
     * @throws IOException If an exception is thrown using the UI.
     * @throws SAXException If an exception is thrown using the UI.
     */
    private void checkUpdateSite(String urlUpdateSite, String message, boolean isSuccess) throws IOException, SAXException {
        // Capture log messages without changing the level
        logging.record(PluginManager.class, Logger.getLogger(PluginManager.class.getName()).getLevel()).capture(50);

        Jenkins.getActiveInstance().getUpdateCenter().getSites().clear();
        UpdateSite us = new UpdateSite("CustomUpdateSite", urlUpdateSite);
        Jenkins.getActiveInstance().getUpdateCenter().getSites().add(us);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
        HtmlPage p = wc.goTo("pluginManager");
        Page pageAfterClick = HtmlElementUtil.click(getCheckNow(p));
        String page = pageAfterClick.getWebResponse().getContentAsString();

        // Check what is shown in the web page
        Assert.assertNotEquals(isSuccess, page.contains(Messages.PluginManager_CheckUpdateServerError(message)));
        // Check the logs (attempted checkUpdateAttempts times). The second argument, the exception does't matter to test the message in the log
        Assert.assertNotEquals(isSuccess, logging.getMessages().stream().anyMatch(m -> m.contains(Messages.PluginManager_UpdateSiteError(PluginManager.checkUpdateAttempts, ""))));
    }

    @TestExtension("updateSiteReturn502Test")
    public static final class FailingWith502UpdateCenterAction implements RootAction {

        @Override
        public @CheckForNull String getIconFileName() {
            return "gear2.png";
        }

        @Override
        public @CheckForNull String getDisplayName() {
            return "Update Site returning 502";
        }

        @Override
        public String getUrlName() {
            return "updateSite502";
        }

        public HttpResponse doGetJson(StaplerRequest request) {
            return HttpResponses.error(502, "Gateway error");
        }
    }

    @TestExtension({"updateSiteWrongJsonTest", "changeLogLevelInLog"})
    public static final class FailingWithWrongJsonUpdateCenterAction implements RootAction {

        @Override
        public @CheckForNull String getIconFileName() {
            return "gear2.png";
        }

        @Override
        public @CheckForNull String getDisplayName() {
            return "Update Site returning wrong json";
        }

        @Override
        public String getUrlName() {
            return "updateSiteWrongJson";
        }

        public void doGetJson(StaplerRequest request, StaplerResponse response) throws IOException {
            response.setContentType("text/json");
            response.setStatus(200);
            response.getWriter().append("{wrongjson}");
        }
    }

    @TestExtension("updateSiteRightJsonTest")
    public static final class ReturnRightJsonUpdateCenterAction implements RootAction {

        @Override
        public @CheckForNull String getIconFileName() {
            return "gear2.png";
        }

        @Override
        public @CheckForNull String getDisplayName() {
            return "Update Site returning right json";
        }

        @Override
        public String getUrlName() {
            return "updateSiteRightJson";
        }

        // The url has to end in update-center.json. See: UpdateSite#getMetadataUrlForDownloadable
        public void doDynamic(StaplerRequest staplerRequest, StaplerResponse staplerResponse) throws ServletException, IOException {
            staplerResponse.setContentType("text/json");
            staplerResponse.setStatus(200);
            staplerResponse.serveFile(staplerRequest, UpdateCenterErrorsTest.class.getResource("update-center.json"));
        }
    }

}
