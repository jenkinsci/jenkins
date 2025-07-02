package hudson;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import hudson.model.DownloadService;
import hudson.model.RootAction;
import hudson.model.UpdateSite;
import hudson.model.UpdateSiteTest;
import hudson.util.HttpResponses;
import hudson.util.Retrier;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.htmlunit.Page;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.xml.sax.SAXException;

@WithJenkins
class PluginManagerCheckUpdateCenterTest {

    private final LogRecorder logging = new LogRecorder();

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Check if the page contains the right message after checking an update site with an url that returns a 502 error code.
     * @throws Exception If there are errors managing the web elements.
     */
    @Test
    void updateSiteReturn502Test() throws Exception {
        checkUpdateSite(Jenkins.get().getRootUrl() + "updateSite502/getJson", "IOException: Server returned HTTP response code: 502 for URL", false);
    }

    /**
     * Check if the page contains the right message after checking an update site with an url that returns a wrong json.
     * @throws Exception If there are errors managing the web elements.
     */
    @Test
    void updateSiteWrongJsonTest() throws Exception {
        checkUpdateSite(Jenkins.get().getRootUrl() + "updateSiteWrongJson/getJson", "JSONException: Unquotted string 'wrongjson'", false);
    }

    /**
     * Check if the page contains the right message after checking an update site that returns a well defined json.
     * @throws Exception If there are errors managing the web elements.
     */
    @Test
    void updateSiteRightJsonTest() throws Exception {
        // Save the actual value to leave it so, when the test finish, just in case it is needed for other tests
        boolean oldValueSignatureCheck = DownloadService.signatureCheck;
        try {
            //Avoid CertPathValidatorException: Algorithm constraints check failed on signature algorithm: MD5withRSA
            DownloadService.signatureCheck = false;
            // Have to end in update-center.json or it fails. See UpdateSite#getMetadataUrlForDownloadable
            checkUpdateSite(Jenkins.get().getRootUrl() + "updateSiteRightJson/update-center.json", "", true);
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
    void changeLogLevelInLog() throws Exception {
        Logger pmLogger = Logger.getLogger(PluginManager.class.getName());
        Logger rLogger = Logger.getLogger(Retrier.class.getName());

        // save current level (to avoid interfering other tests)
        Level pmLevel = pmLogger.getLevel();
        Level rLevel = rLogger.getLevel();

        try {
            // set level to record
            pmLogger.setLevel(Level.SEVERE);
            rLogger.setLevel(Level.SEVERE);

            // check with more than 1 attempt and level > WARNING
            PluginManager.CHECK_UPDATE_ATTEMPTS = 2;
            updateSiteWrongJsonTest();

            // the messages has been recorded in the log
            assertThat(logging, LogRecorder.recorded(is(Messages.PluginManager_UpdateSiteChangeLogLevel(Retrier.class.getName()))));
        } finally {
            // restore level
            pmLogger.setLevel(pmLevel);
            rLogger.setLevel(rLevel);
        }
    }

    /**
     * Check the update site.
     * @param urlUpdateSite If null, use the default update site, otherwise, use this update site.
     * @param message The message that should exist or not in the page after checking the update site.
     * @param isSuccess If true, test that PluginManager.CheckUpdateServerError + message doesn't exist in the page.
     *                  If false, test that PluginManager.CheckUpdateServerError + message exists in the page.
     * @throws IOException If an exception is thrown using the UI.
     * @throws SAXException If an exception is thrown using the UI.
     */
    private void checkUpdateSite(String urlUpdateSite, String message, boolean isSuccess) throws IOException, SAXException {
        // Capture log messages in the plugin manager and the retrier without changing the level of each logger
        logging.record(PluginManager.class, Logger.getLogger(PluginManager.class.getName()).getLevel());
        logging.record(Retrier.class, Logger.getLogger(Retrier.class.getName()).getLevel()).capture(50);


        Jenkins.get().getUpdateCenter().getSites().clear();
        UpdateSite us = new UpdateSite("CustomUpdateSite", urlUpdateSite);
        Jenkins.get().getUpdateCenter().getSites().add(us);

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
        HtmlPage p = wc.goTo("pluginManager");
        Page pageAfterClick = PluginManagerUtil.getCheckForUpdatesButton(p).click();
        String page = pageAfterClick.getWebResponse().getContentAsString();

        // Check what is shown in the web page
        assertNotEquals(isSuccess, page.contains(Messages.PluginManager_CheckUpdateServerError(message)));

        // Check the logs (attempted CHECK_UPDATE_ATTEMPTS times). The second argument, the exception does't matter to test the message in the log
        assertNotEquals(isSuccess, logging.getMessages().stream().anyMatch(m -> m.contains(Messages.PluginManager_UpdateSiteError(PluginManager.CHECK_UPDATE_ATTEMPTS, ""))));
    }

    @TestExtension("updateSiteReturn502Test")
    public static final class FailingWith502UpdateCenterAction implements RootAction {

        @Override
        public String getIconFileName() {
            return "gear2.png";
        }

        @Override
        public String getDisplayName() {
            return "Update Site returning 502";
        }

        @Override
        public String getUrlName() {
            return "updateSite502";
        }

        public HttpResponse doGetJson(StaplerRequest2 request) {
            return HttpResponses.error(502, "Gateway error");
        }
    }

    @TestExtension({"updateSiteWrongJsonTest", "changeLogLevelInLog"})
    public static final class FailingWithWrongJsonUpdateCenterAction implements RootAction {

        @Override
        public String getIconFileName() {
            return "gear2.png";
        }

        @Override
        public String getDisplayName() {
            return "Update Site returning wrong json";
        }

        @Override
        public String getUrlName() {
            return "updateSiteWrongJson";
        }

        public void doGetJson(StaplerRequest2 request, StaplerResponse2 response) throws IOException {
            response.setContentType("text/json");
            response.setStatus(200);
            response.getWriter().append("{wrongjson}");
        }
    }

    @TestExtension("updateSiteRightJsonTest")
    public static final class ReturnRightJsonUpdateCenterAction implements RootAction {

        @Override
        public String getIconFileName() {
            return "gear2.png";
        }

        @Override
        public String getDisplayName() {
            return "Update Site returning right json";
        }

        @Override
        public String getUrlName() {
            return "updateSiteRightJson";
        }

        // The url has to end in update-center.json. See: UpdateSite#getMetadataUrlForDownloadable
        public void doDynamic(StaplerRequest2 staplerRequest, StaplerResponse2 staplerResponse) throws ServletException, IOException {
            staplerResponse.setContentType("text/json");
            staplerResponse.setStatus(200);
            staplerResponse.serveFile(staplerRequest,  UpdateSiteTest.extract("update-center.json"));
        }
    }

}
