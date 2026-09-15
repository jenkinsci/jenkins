package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.userproperty.UserPropertyCategory;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.htmlunit.FormEncodingType;
import org.htmlunit.HttpMethod;
import org.htmlunit.Page;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.RequestImpl;
import org.kohsuke.stapler.StaplerRequest2;

@WithJenkins
class Security3926Test {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        TestProperty.staticField = null;
    }

    @Test
    @Issue("SECURITY-3926")
    void bindJSONCannotSetPublicStaticField() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy authStrategy = new MockAuthorizationStrategy();
        authStrategy.grant(Jenkins.READ).everywhere().to("alice");
        j.jenkins.setAuthorizationStrategy(authStrategy);

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wc.login("alice");

            int slot = UserProperty.allByCategoryClass(UserPropertyCategory.Preferences.class).stream()
                    .map(d -> d.clazz).toList().indexOf(TestProperty.class);
            String json = "{\"userProperty" + slot
                    + "\":{\"instanceField\":\"written\",\"staticField\":\"p0wn3d\"}}";

            WebRequest req = new WebRequest(
                    wc.createCrumbedUrl("user/alice/preferences/configSubmit"), HttpMethod.POST);
            req.setEncodingType(FormEncodingType.URL_ENCODED);
            req.setRequestBody("core%3Aapply=true&json=" + URLEncoder.encode(json, StandardCharsets.UTF_8));

            Page page = wc.getPage(req);
            assertEquals(200, page.getWebResponse().getStatusCode());
        }

        TestProperty property = User.getOrCreateByIdOrFullName("alice").getProperty(TestProperty.class);
        assertEquals("written", property.instanceField);
        assertNull(TestProperty.staticField);
    }

    @Test
    @Issue("SECURITY-3926")
    void escapeHatchAllowsSettingPublicStaticField() throws Exception {
        Field escapeHatch = RequestImpl.class.getDeclaredField("ALLOW_STATIC_FIELD_BINDING");
        escapeHatch.setAccessible(true);
        escapeHatch.set(null, true);
        try {
            j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
            MockAuthorizationStrategy authStrategy = new MockAuthorizationStrategy();
            authStrategy.grant(Jenkins.READ).everywhere().to("alice");
            j.jenkins.setAuthorizationStrategy(authStrategy);

            try (JenkinsRule.WebClient wc = j.createWebClient()) {
                wc.login("alice");

                int slot = UserProperty.allByCategoryClass(UserPropertyCategory.Preferences.class).stream()
                        .map(d -> d.clazz).toList().indexOf(TestProperty.class);
                String json = "{\"userProperty" + slot
                        + "\":{\"instanceField\":\"written\",\"staticField\":\"p0wn3d\"}}";

                WebRequest req = new WebRequest(
                        wc.createCrumbedUrl("user/alice/preferences/configSubmit"), HttpMethod.POST);
                req.setEncodingType(FormEncodingType.URL_ENCODED);
                req.setRequestBody("core%3Aapply=true&json=" + URLEncoder.encode(json, StandardCharsets.UTF_8));

                Page page = wc.getPage(req);
                assertEquals(200, page.getWebResponse().getStatusCode());
            }

            TestProperty property = User.getOrCreateByIdOrFullName("alice").getProperty(TestProperty.class);
            assertEquals("written", property.instanceField);
            assertEquals("p0wn3d", TestProperty.staticField);
        } finally {
            escapeHatch.set(null, false);
        }
    }

    public static class TestProperty extends UserProperty {

        public static String staticField;

        public String instanceField;

        @Override
        public UserProperty reconfigure(StaplerRequest2 req, JSONObject form) throws Descriptor.FormException {
            req.bindJSON(this, form);
            return this;
        }

        @TestExtension
        public static class DescriptorImpl extends UserPropertyDescriptor {

            @NonNull
            @Override
            public String getDisplayName() {
                return "SECURITY-3926 test property";
            }

            @Override
            public UserProperty newInstance(User user) {
                return new TestProperty();
            }

            @NonNull
            @Override
            public UserPropertyCategory getUserPropertyCategory() {
                return UserPropertyCategory.get(UserPropertyCategory.Preferences.class);
            }
        }
    }
}
