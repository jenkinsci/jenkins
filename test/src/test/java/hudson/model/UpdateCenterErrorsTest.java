package hudson.model;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.DomNodeList;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlElementUtil;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.Messages;
import hudson.PluginManagerTest;
import hudson.util.HttpResponses;
import jenkins.model.Jenkins;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.xml.sax.SAXException;

import javax.annotation.CheckForNull;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

public class UpdateCenterErrorsTest {
    private static final Logger LOGGER = Logger.getLogger(Jenkins.class.getName());

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Check if the page contains the right message after checking an update site with an url that returns a 502 error code.
     * @throws Exception If there are errors managing the web elements.
     */
    @Test
    public void updateSiteReturn502Test() throws Exception {
        checkUpdateSite(Jenkins.getInstance().getRootUrl() + "updateSite502/getJson", "Server returned HTTP response code: 502 for URL", true);
    }

    /**
     * Check if the page contains the right message after checking an update site with an url that returns a wrong json.
     * @throws Exception If there are errors managing the web elements.
     */
    @Test
    public void updateSiteWrongJsonTest() throws Exception {
        checkUpdateSite(Jenkins.getInstance().getRootUrl() + "updateSiteWrongJson/getJson", "Unquotted string 'wrongjson'", true);
    }

    /**
     * Check if the page contains the right message after checking an update site that returns a well defined json.
     * @throws Exception If there are errors managing the web elements.
     */
    @Test
    public void updateSiteRightJsonTest() throws Exception {
        //Avoid CertPathValidatorException: Algorithm constraints check failed on signature algorithm: MD5withRSA
        DownloadService.signatureCheck = false;
        checkUpdateSite(UpdateCenterErrorsTest.class.getResource("update-center.json").toString(), "", false );
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
        if(urlUpdateSite != null) {
            Jenkins.getActiveInstance().getUpdateCenter().getSites().clear();
            UpdateSite us = new UpdateSite("CustomUpdateSite", urlUpdateSite);
            Jenkins.getActiveInstance().getUpdateCenter().getSites().add(us);
        }

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
        HtmlPage p = wc.goTo("pluginManager");
        Page pageAfterClick = HtmlElementUtil.click(getCheckNow(p));
        String page = pageAfterClick.getWebResponse().getContentAsString();

        if(isSuccess)
            Assert.assertTrue(page.contains(Messages.PluginManager_CheckUpdateServerError(message)));
        else
            Assert.assertFalse(page.contains(Messages.PluginManager_CheckUpdateServerError(message)));
    }

    @TestExtension("updateSiteReturn502Test")
    public static final class FailingWith502UpdateCenterAction implements RootAction {

        @Override
        public @CheckForNull
        String getIconFileName() {
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

        //@WebMethod(name = "submit")
        public HttpResponse doGetJson(StaplerRequest request) {
            return HttpResponses.error(502, "Gateway error");
        }
    }

    @TestExtension("updateSiteWrongJsonTest")
    public static final class FailingWithWronJsonUpdateCenterAction implements RootAction {

        @Override
        public @CheckForNull
        String getIconFileName() {
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

        //@WebMethod(name = "submit")
        public HttpResponse doGetJson(StaplerRequest request) {
            HttpResponse r = new HttpResponse() {
                @Override
                public void generateResponse(StaplerRequest staplerRequest, StaplerResponse staplerResponse, Object o) throws IOException, ServletException {
                    staplerResponse.setContentType("text/json");
                    staplerResponse.setStatus(200);
                    PrintWriter w = staplerResponse.getWriter();
                    w.append("{wrongjson}");
                }
            };

            return r;
        }
    }

}
