package hudson;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gargoylesoftware.htmlunit.AlertHandler;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlElementUtil;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.DownloadService;
import hudson.model.RootAction;
import hudson.model.UpdateSite;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

public class PluginManagerSecurity3037Test {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public FlagRule<Boolean> signatureCheck = new FlagRule<>(() -> DownloadService.signatureCheck, x -> DownloadService.signatureCheck = x);

    @Test
    public void noInjectionOnAvailablePluginsPage() throws Exception {
        DownloadService.signatureCheck = false;
        Jenkins.get().getUpdateCenter().getSites().clear();
        UpdateSite us = new UpdateSite("Security3037", Jenkins.get().getRootUrl() + "security3037UpdateCenter/update-center.json");
        Jenkins.get().getUpdateCenter().getSites().add(us);

        try (JenkinsRule.WebClient wc = r.createWebClient()) {
            HtmlPage p = wc.goTo("pluginManager");
            List<HtmlElement> elements = p.getElementById("bottom-sticker")
                    .getElementsByTagName("a")
                    .stream()
                    .filter(link -> link.getAttribute("href").equals("checkUpdatesServer"))
                    .collect(Collectors.toList());
            assertEquals(1, elements.size());
            AlertHandlerImpl alertHandler = new AlertHandlerImpl();
            wc.setAlertHandler(alertHandler);

            HtmlElementUtil.click(elements.get(0));
            HtmlPage available = wc.goTo("pluginManager/available");
            assertTrue(available.querySelector(".alert-danger")
                    .getTextContent().contains("This plugin is built for Jenkins 2.999"));
            wc.waitForBackgroundJavaScript(100);

            HtmlAnchor anchor = available.querySelector(".jenkins-table__link");
            anchor.click(true, false, false);
            wc.waitForBackgroundJavaScript(100);
            assertTrue(alertHandler.messages.isEmpty());
        }
    }

    static class AlertHandlerImpl implements AlertHandler {
        List<String> messages = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void handleAlert(final Page page, final String message) {
            messages.add(message);
        }
    }

    @TestExtension("noInjectionOnAvailablePluginsPage")
    public static final class Security3037UpdateCenter implements RootAction {

        @Override
        public String getIconFileName() {
            return "gear2.png";
        }

        @Override
        public String getDisplayName() {
            return "security-3037-update-center";
        }

        @Override
        public String getUrlName() {
            return "security3037UpdateCenter";
        }

        public void doDynamic(StaplerRequest staplerRequest, StaplerResponse staplerResponse) throws ServletException, IOException {
            staplerResponse.setContentType("application/json");
            staplerResponse.setStatus(200);
            staplerResponse.serveFile(staplerRequest, PluginManagerSecurity3037Test.class.getResource("PluginManagerSecurity3037Test/update-center.json"));
        }
    }
}
