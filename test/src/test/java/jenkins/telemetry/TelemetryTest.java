package jenkins.telemetry;

import hudson.ExtensionList;
import hudson.model.UnprotectedRootAction;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

import hudson.security.csrf.CrumbExclusion;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

public class TelemetryTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule logger = new LoggerRule().record(Telemetry.class, Level.ALL).capture(100);

    private static int counter = 0;

    @Test
    public void testSubmission() throws Exception {
        j.jenkins.setNoUsageStatistics(false); // tests usually don't submit this, but we need this
        assertEquals("no requests received", 0, counter);
        Telemetry.ENDPOINT = j.getURL().toString() + "uplink/events";
        ExtensionList.lookupSingleton(Telemetry.TelemetryReporter.class).doRun();
        do {
            Thread.sleep(250);
        } while (counter == 0); // this might end up being flaky due to 1 to many active telemetry trials
        assertThat(logger.getMessages(), hasItem("Telemetry submission received response '200 OK' for: test-data"));
        assertThat(logger.getMessages(), hasItem("Skipping telemetry for 'future' as it is configured to start later"));
        assertThat(logger.getMessages(), hasItem("Skipping telemetry for 'past' as it is configured to end in the past"));
        assertThat(types, hasItem("test-data"));
        assertThat(types, not(hasItem("future")));
        assertThat(types, not(hasItem("past")));
        assertThat(correlators.size(), is(1));
        assertTrue("at least one request received", counter > 0); // TestTelemetry plus whatever real impls exist
    }

    @TestExtension
    public static class DisabledFutureTelemetry extends Telemetry {

        @Nonnull
        @Override
        public String getId() {
            return "future";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "future";
        }

        @Nonnull
        @Override
        public LocalDate getStart() {
            return LocalDate.now().plus(1, ChronoUnit.DAYS);
        }

        @Nonnull
        @Override
        public LocalDate getEnd() {
            return LocalDate.MAX;
        }

        @Nonnull
        @Override
        public JSONObject createContent() {
            return new JSONObject();
        }
    }

    @TestExtension
    public static class DisabledPastTelemetry extends Telemetry {

        @Nonnull
        @Override
        public String getId() {
            return "past";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "past";
        }

        @Nonnull
        @Override
        public LocalDate getStart() {
            return LocalDate.MIN;
        }

        @Nonnull
        @Override
        public LocalDate getEnd() {
            return LocalDate.now().minus(1, ChronoUnit.DAYS);
        }

        @Nonnull
        @Override
        public JSONObject createContent() {
            return new JSONObject();
        }
    }

    @TestExtension
    public static class TestTelemetry extends Telemetry {

        @Nonnull
        @Override
        public String getId() {
            return "test-data";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "test-data";
        }

        @Nonnull
        @Override
        public LocalDate getStart() {
            return LocalDate.MIN;
        }

        @Nonnull
        @Override
        public LocalDate getEnd() {
            return LocalDate.MAX;
        }

        @Nonnull
        @Override
        public JSONObject createContent() {
            return new JSONObject();
        }
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

    private static Set<String> correlators = new HashSet<>();
    private static Set<String> types = new HashSet<>();

    @TestExtension
    public static class TelemetryReceiver implements UnprotectedRootAction {
        public void doEvents(StaplerRequest request, StaplerResponse response) throws IOException {
            StringWriter sw = new StringWriter();
            IOUtils.copy(request.getInputStream(), sw, StandardCharsets.UTF_8);
            JSONObject json = JSONObject.fromObject(sw.toString());
            correlators.add(json.getString("correlator"));
            types.add(json.getString("type"));
            counter++;
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
