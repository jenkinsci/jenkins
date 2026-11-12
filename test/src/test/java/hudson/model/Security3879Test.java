package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.sf.json.JSONObject;
import org.htmlunit.WebResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@WithJenkins
class Security3879Test {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("SECURITY-3879")
    void mapKeyIsEscapedInJson() throws Exception {
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            WebResponse response = wc.goTo("mapKeyEscape/api/json", "application/json").getWebResponse();
            JSONObject json = JSONObject.fromObject(response.getContentAsString());
            JSONObject envVars = json.getJSONObject("envVars");

            assertThat(envVars.keySet(), equalTo(Set.of(MapHolder.CRAFTED_KEY)));
            assertThat(envVars, not(hasKey("injected")));
            assertThat(envVars.getString(MapHolder.CRAFTED_KEY), equalTo("value"));
        }
    }

    @Test
    @Issue("SECURITY-3879")
    void mapKeyIsEscapedInPython() throws Exception {
        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            WebResponse response = wc.goTo("mapKeyEscape/api/python", "text/x-python").getWebResponse();
            String body = response.getContentAsString();

            assertThat(body, containsString("\\\""));
            assertThat(body, not(containsString("\"pwned\",\"injected\":")));
        }
    }

    @TestExtension
    public static class MapKeyEscapeRootAction implements RootAction {
        @Override
        public @CheckForNull String getIconFileName() {
            return null;
        }

        @Override
        public @CheckForNull String getDisplayName() {
            return null;
        }

        @Override
        public @CheckForNull String getUrlName() {
            return "mapKeyEscape";
        }

        public Api getApi() {
            return new Api(new MapHolder());
        }
    }

    @ExportedBean
    public static class MapHolder {
        static final String CRAFTED_KEY = "escape\":\"pwned\",\"injected";

        @Exported
        public Map<String, String> getEnvVars() {
            Map<String, String> m = new LinkedHashMap<>();
            m.put(CRAFTED_KEY, "value");
            return m;
        }
    }
}
