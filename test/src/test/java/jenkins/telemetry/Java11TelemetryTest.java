package jenkins.telemetry;

import hudson.ExtensionList;
import hudson.model.UnprotectedRootAction;
import hudson.security.csrf.CrumbExclusion;
import net.sf.json.JSONObject;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.annotation.CheckForNull;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class Java11TelemetryTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private static JSONObject received = null;

    @Before
    public void prepare() throws Exception {
        received = null;
        Telemetry.ENDPOINT = j.getURL().toString() + "uplink/events";
        j.jenkins.setNoUsageStatistics(false); // tests usually don't submit this, but we need this
    }


    @Test
    public void infoSentTest() throws InterruptedException {
        Correlator correlator = ExtensionList.lookupSingleton(Correlator.class);
        String correlationId = "00000000-0000-0000-0000-000000000000";
        correlator.setCorrelationId(correlationId);

        ExtensionList.lookupSingleton(Telemetry.TelemetryReporter.class).doRun();
        do {
            Thread.sleep(250);
        } while (received == null); // this might end up being flaky due to 1 to many active telemetry trials

        assertEquals(this.getClass().getName(), received.getString("type"));
        //90ecf3ce1cd5ba1e5ad3cde7ad08a941e884f2e4d9bd463361715abab8efedc5
        assertEquals(DigestUtils.sha256Hex(correlationId + "test-data"), received.getString("correlator"));
    }

    @TestExtension
    public static class NoCrumb extends CrumbExclusion {
        @Override
        public boolean process(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
            String pathInfo = request.getPathInfo();
            if (pathInfo != null && pathInfo.startsWith("/uplink")) {
                chain.doFilter(request, response);
                return true;
            }
            return false;
        }
    }

    @TestExtension
    public static class TelemetryReceiver implements UnprotectedRootAction {
        public void doEvents(StaplerRequest request, StaplerResponse response) throws IOException {
            StringWriter sw = new StringWriter();
            IOUtils.copy(request.getInputStream(), sw, StandardCharsets.UTF_8);
            JSONObject json = JSONObject.fromObject(sw.toString());
            correlators.add(json.getString("correlator"));
            types.add(json.getString("type"));
            received = true;
        }

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
            return "uplink";
        }
    }
}
