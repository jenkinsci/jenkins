package jenkins.telemetry;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.UnprotectedRootAction;
import hudson.security.csrf.CrumbExclusion;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Pattern;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

public class TelemetryTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule logger = new LoggerRule().record(Telemetry.class, Level.ALL).capture(100);

    private static int counter = 0;

    @Before
    public void prepare() throws Exception {
        correlators.clear();
        types.clear();
        counter = 0;
        j.jenkins.setNoUsageStatistics(false); // tests usually don't submit this, but we need this
        Telemetry.ENDPOINT = j.getURL().toString() + "uplink/events";
    }

    @Test
    public void testSubmission() throws Exception {
        j.jenkins.setNoUsageStatistics(false); // tests usually don't submit this, but we need this
        assertEquals("no requests received", 0, counter);
        ExtensionList.lookupSingleton(Telemetry.TelemetryReporter.class).doRun();
        await().pollInterval(250, TimeUnit.MILLISECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .until(logger::getMessages, hasItem("Telemetry submission received response 200 for: test-data"));
        await().pollInterval(250, TimeUnit.MILLISECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .until(logger::getMessages, hasItem("Skipping telemetry for 'future' as it is configured to start later"));
        await().pollInterval(250, TimeUnit.MILLISECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .until(logger::getMessages, hasItem("Skipping telemetry for 'past' as it is configured to end in the past"));
        await().pollInterval(250, TimeUnit.MILLISECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .until(logger::getMessages, hasItem("Skipping telemetry for 'empty' as it has no data"));
        await().pollInterval(250, TimeUnit.MILLISECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> types, hasItem("test-data"));
        assertThat(types, not(hasItem("future")));
        assertThat(types, not(hasItem("past")));
        assertThat(correlators.size(), is(counter));
        assertTrue(Pattern.compile("[0-9a-f]+").matcher(correlators.first()).matches());
        assertThat(types, not(hasItem("empty")));
        assertTrue("at least one request received", counter > 0); // TestTelemetry plus whatever real impls exist
    }

    @Test
    public void testPerTrialCorrelator() throws Exception {
        Correlator correlator = ExtensionList.lookupSingleton(Correlator.class);
        String correlationId = "00000000-0000-0000-0000-000000000000";
        correlator.setCorrelationId(correlationId);

        ExtensionList.lookupSingleton(Telemetry.TelemetryReporter.class).doRun();
        await().pollInterval(250, TimeUnit.MILLISECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> types, hasItem("test-data"));
        //90ecf3ce1cd5ba1e5ad3cde7ad08a941e884f2e4d9bd463361715abab8efedc5
        assertThat(correlators, hasItem(Util.getHexOfSHA256DigestOf(correlationId + "test-data")));
    }

    @Test
    public void testNonSubmissionOnError() throws Exception {
        assertEquals("no requests received", 0, counter);
        ExtensionList.lookupSingleton(Telemetry.TelemetryReporter.class).doRun();
        await().pollInterval(250, TimeUnit.MILLISECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .until(logger::getMessages, hasItem("Failed to build telemetry content for: 'throwing'"));
        await().pollInterval(250, TimeUnit.MILLISECONDS)
                .atMost(2, TimeUnit.SECONDS)
                .until(logger::getMessages, hasItem("Skipping telemetry for 'throwing' as it has no data"));
        await().pollInterval(250, TimeUnit.MILLISECONDS)
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> types, is(not(empty())));
        assertThat(types, not(contains("throwing")));
    }

    @TestExtension("testNonSubmissionOnError")
    public static class ExceptionThrowingTelemetry extends Telemetry {

        @NonNull
        @Override
        public String getDisplayName() {
            return "throwing";
        }

        @NonNull
        @Override
        public String getId() {
            return "throwing";
        }

        @NonNull
        @Override
        public LocalDate getStart() {
            return LocalDate.MIN;
        }

        @NonNull
        @Override
        public LocalDate getEnd() {
            return LocalDate.MAX;
        }

        @Override
        public JSONObject createContent() {
            throw new RuntimeException("something went wrong");
        }
    }

    @TestExtension
    public static class EmptyTelemetry extends Telemetry {

        @NonNull
        @Override
        public String getDisplayName() {
            return "empty";
        }

        @NonNull
        @Override
        public String getId() {
            return "empty";
        }

        @NonNull
        @Override
        public LocalDate getStart() {
            return LocalDate.MIN;
        }

        @NonNull
        @Override
        public LocalDate getEnd() {
            return LocalDate.MAX;
        }

        @Override
        public JSONObject createContent() {
            return null;
        }
    }

    @TestExtension
    public static class DisabledFutureTelemetry extends Telemetry {

        @NonNull
        @Override
        public String getId() {
            return "future";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "future";
        }

        @NonNull
        @Override
        public LocalDate getStart() {
            return LocalDate.now().plusDays(1);
        }

        @NonNull
        @Override
        public LocalDate getEnd() {
            return LocalDate.MAX;
        }

        @NonNull
        @Override
        public JSONObject createContent() {
            return new JSONObject();
        }
    }

    @TestExtension
    public static class DisabledPastTelemetry extends Telemetry {

        @NonNull
        @Override
        public String getId() {
            return "past";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "past";
        }

        @NonNull
        @Override
        public LocalDate getStart() {
            return LocalDate.MIN;
        }

        @NonNull
        @Override
        public LocalDate getEnd() {
            return LocalDate.now().minusDays(1);
        }

        @NonNull
        @Override
        public JSONObject createContent() {
            return new JSONObject();
        }
    }

    @TestExtension
    public static class TestTelemetry extends Telemetry {

        @NonNull
        @Override
        public String getId() {
            return "test-data";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "test-data";
        }

        @NonNull
        @Override
        public LocalDate getStart() {
            return LocalDate.MIN;
        }

        @NonNull
        @Override
        public LocalDate getEnd() {
            return LocalDate.MAX;
        }

        @NonNull
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

    private static SortedSet<String> correlators = new TreeSet<>();
    private static Set<String> types = new HashSet<>();

    @TestExtension
    public static class TelemetryReceiver implements UnprotectedRootAction {
        public void doEvents(StaplerRequest2 request, StaplerResponse2 response) throws IOException {
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
