package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.views.LastStableColumn;
import hudson.views.ListViewColumn;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import jenkins.model.Jenkins;
import org.htmlunit.FormEncodingType;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;

@WithJenkins
class Security3915Test {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
        NonColumnGadget.reset();
    }

    @Test
    @Issue("SECURITY-3915")
    void nonColumnGadgetIsRejectedFromListViewColumns() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy authStrategy = new MockAuthorizationStrategy();
        authStrategy.grant(Jenkins.READ).everywhere().to("alice");
        j.jenkins.setAuthorizationStrategy(authStrategy);

        try (JenkinsRule.WebClient wc = j.createWebClient().withThrowExceptionOnFailingStatusCode(false)) {
            wc.login("alice");

            String json = "{"
                    + "\"name\":\"leak-test\","
                    + "\"mode\":\"hudson.model.ListView\","
                    + "\"columns\":{"
                    + "\"stapler-class-bag\":true,"
                    + "\"" + NonColumnGadget.class.getName().replace('.', '-') + "\":{}"
                    + "},"
                    + "\"jobFilters\":[]"
                    + "}";
            WebRequest req = new WebRequest(wc.createCrumbedUrl("/user/alice/my-views/createView"), HttpMethod.POST);
            req.setEncodingType(FormEncodingType.URL_ENCODED);
            req.setRequestBody("name=leak-test&mode=hudson.model.ListView&json="
                    + URLEncoder.encode(json, StandardCharsets.UTF_8));
            wc.getPage(req);
        }

        assertEquals(0, NonColumnGadget.constructionCount());
        MyViewsProperty property = User.getOrCreateByIdOrFullName("alice").getProperty(MyViewsProperty.class);
        View view = property.getView("leak-test");
        if (view instanceof ListView) {
            for (ListViewColumn column : ((ListView) view).getColumns()) {
                assertNotEquals(NonColumnGadget.class.getName(), column.getClass().getName());
            }
        }
    }

    @Test
    @Issue("SECURITY-3915")
    void legitimateListViewColumnStillBinds() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy authStrategy = new MockAuthorizationStrategy();
        authStrategy.grant(Jenkins.READ).everywhere().to("alice");
        j.jenkins.setAuthorizationStrategy(authStrategy);

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wc.login("alice");

            String json = "{"
                    + "\"name\":\"legit\","
                    + "\"mode\":\"hudson.model.ListView\","
                    + "\"columns\":{"
                    + "\"stapler-class-bag\":true,"
                    + "\"" + LastStableColumn.class.getName().replace('.', '-') + "\":{}"
                    + "},"
                    + "\"jobFilters\":[]"
                    + "}";
            WebRequest req = new WebRequest(wc.createCrumbedUrl("/user/alice/my-views/createView"), HttpMethod.POST);
            req.setEncodingType(FormEncodingType.URL_ENCODED);
            req.setRequestBody("name=legit&mode=hudson.model.ListView&json="
                    + URLEncoder.encode(json, StandardCharsets.UTF_8));
            wc.getPage(req);
        }

        MyViewsProperty property = User.getOrCreateByIdOrFullName("alice").getProperty(MyViewsProperty.class);
        View view = property.getView("legit");
        assertNotNull(view);
        assertTrue(view instanceof ListView);
        List<ListViewColumn> columns = ((ListView) view).getColumns();
        assertThat(columns, hasItem(instanceOf(LastStableColumn.class)));
    }

    public static final class NonColumnGadget extends AbstractDescribableImpl<NonColumnGadget> {

        private static int constructions = 0;

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public NonColumnGadget() {
            constructions++;
        }

        static void reset() {
            constructions = 0;
        }

        static int constructionCount() {
            return constructions;
        }

        @TestExtension
        public static class DescriptorImpl extends Descriptor<NonColumnGadget> {
            @NonNull
            @Override
            public String getDisplayName() {
                return "SECURITY-3915 test gadget";
            }
        }
    }
}
