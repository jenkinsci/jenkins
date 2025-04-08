package jenkins.health;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

import java.util.logging.Level;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;


public class HealthCheckActionTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public LoggerRule loggingRule = new LoggerRule().record(HealthCheckAction.class, Level.WARNING).capture(10);

    @Test
    public void healthCheck() throws Exception {
        try (var webClient = r.createWebClient()) {
            var page = webClient.goTo("healthCheck", "application/json");
            assertThat(page.getWebResponse().getStatusCode(), is(200));
            assertEquals(new JSONObject().element("status", true).element("data", new JSONArray()), JSONObject.fromObject(page.getWebResponse().getContentAsString()));
        }
    }

    @Test
    public void healthCheckSuccessExtension() throws Exception {
        try (var webClient = r.createWebClient()) {
            var page = webClient.goTo("healthCheck", "application/json");
            assertThat(page.getWebResponse().getStatusCode(), is(200));
            assertEquals(JSONObject.fromObject("""
            {
                "status": true,
                "data": [
                  {
                    "name": "success",
                    "result": true
                  }
                ]
            }
            """), JSONObject.fromObject(page.getWebResponse().getContentAsString()));
        }
    }

    @TestExtension({"healthCheckSuccessExtension", "healthCheckFailingExtension"})
    public static class SuccessHealthCheck implements HealthCheck {

        @Override
        public String getName() {
            return "success";
        }

        @Override
        public boolean check() {
            return true;
        }
    }

    @Test
    public void healthCheckFailingExtension() throws Exception {
        try (var webClient = r.createWebClient()) {
            webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
            webClient.getOptions().setPrintContentOnFailingStatusCode(false);
            var page = webClient.goTo("healthCheck", "application/json");
            assertThat(page.getWebResponse().getStatusCode(), is(503));
            assertEquals(JSONObject.fromObject("""
            {
                "status": false,
                "data": [
                  {
                    "name": "failing",
                    "result": false
                  },
                  {
                    "name": "success",
                    "result": true
                  }
                ]
            }
            """), JSONObject.fromObject(page.getWebResponse().getContentAsString()));
        }
    }

    @TestExtension("healthCheckFailingExtension")
    public static class FailingHealthCheck implements HealthCheck {

        @Override
        public String getName() {
            return "failing";
        }

        @Override
        public boolean check() {
            return false;
        }
    }

    @Test
    public void duplicateHealthCheckExtension() throws Exception {
        try (var webClient = r.createWebClient()) {
            var page = webClient.goTo("healthCheck", "application/json");
            assertThat(page.getWebResponse().getStatusCode(), is(200));
            assertEquals(JSONObject.fromObject("""
            {
                "status": true,
                "data": [
                  {
                    "name": "dupe",
                    "result": true
                  }
                ]
            }
            """), JSONObject.fromObject(page.getWebResponse().getContentAsString()));
        }
        // TestExtension doesn't have ordinal, but I think by default extensions are ordered alphabetically
        assertThat(loggingRule.getMessages(), contains("Ignoring duplicate health check with name dupe from " + HealthCheck2.class.getName() + " as it is already defined by " + HealthCheck1.class.getName()));
    }

    @TestExtension("duplicateHealthCheckExtension")
    public static class HealthCheck1 implements HealthCheck {

        @Override
        public String getName() {
            return "dupe";
        }

        @Override
        public boolean check() {
            return true;
        }
    }

    @TestExtension("duplicateHealthCheckExtension")
    public static class HealthCheck2 implements HealthCheck {

        @Override
        public String getName() {
            return "dupe";
        }

        @Override
        public boolean check() {
            return false;
        }
    }
}
