package jenkins.telemetry.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

import hudson.ExtensionList;
import hudson.model.FreeStyleProject;
import jenkins.security.csp.AvatarContributor;
import jenkins.security.csp.Contributor;
import jenkins.security.csp.CspBuilder;
import jenkins.security.csp.CspHeader;
import jenkins.security.csp.Directive;
import jenkins.security.csp.impl.BaseContributor;
import jenkins.security.csp.impl.CompatibleContributor;
import jenkins.security.csp.impl.DevelopmentHeaderDecider;
import jenkins.security.csp.impl.UserAvatarContributor;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
public class ContentSecurityPolicyTest {

    @Test
    void basics(JenkinsRule j) { // arg required to actually get a Jenkins
        final ContentSecurityPolicy csp = ExtensionList.lookupSingleton(ContentSecurityPolicy.class);
        final JSONObject content = csp.createContent();
        assertThat(content.get("enforce"), is(false));
        assertThat(content.get("decider"), is(DevelopmentHeaderDecider.class.getName()));
        assertThat(content.get("header"), is(CspHeader.ContentSecurityPolicy.getHeaderName()));
        assertThat(content.getJSONArray("contributors"),
                containsInAnyOrder(BaseContributor.class.getName(), CompatibleContributor.class.getName(), AvatarContributor.class.getName(), UserAvatarContributor.class.getName()));
        assertThat(content.getJSONArray("configurations"), is(empty()));
        final JSONObject directivesSize = content.getJSONObject("directivesSize");
        assertThat(directivesSize.keySet(), containsInAnyOrder(Directive.DEFAULT_SRC, Directive.SCRIPT_SRC, Directive.STYLE_SRC, Directive.IMG_SRC, Directive.FORM_ACTION, Directive.BASE_URI, Directive.FRAME_ANCESTORS));
        assertThat(directivesSize.getJSONObject(Directive.IMG_SRC).get("entries"), is(2));
        assertThat(directivesSize.getJSONObject(Directive.IMG_SRC).get("chars"), is(12)); // 'self' data:
        assertThat(directivesSize.getJSONObject(Directive.SCRIPT_SRC).get("entries"), is(2));
        assertThat(directivesSize.getJSONObject(Directive.SCRIPT_SRC).get("chars"), is(22)); // 'self' 'report-sample'
    }


    @Test
    void withContributors(JenkinsRule j) throws Exception {
        final FreeStyleProject freeStyleProject = j.createFreeStyleProject();

        final ContentSecurityPolicy csp = ExtensionList.lookupSingleton(ContentSecurityPolicy.class);
        final JSONObject content = csp.createContent();
        final JSONObject directivesSize = content.getJSONObject("directivesSize");
        assertThat(directivesSize.keySet(), containsInAnyOrder(Directive.DEFAULT_SRC, Directive.SCRIPT_SRC, Directive.STYLE_SRC, Directive.IMG_SRC, Directive.FORM_ACTION, Directive.BASE_URI, Directive.FRAME_ANCESTORS));
        assertThat(directivesSize.getJSONObject(Directive.IMG_SRC).get("entries"), is(3));
        assertThat(directivesSize.getJSONObject(Directive.IMG_SRC).get("chars"), is(28)); // 'self' data: img.example.com
        assertThat(directivesSize.getJSONObject(Directive.SCRIPT_SRC).get("entries"), is(2));
        assertThat(directivesSize.getJSONObject(Directive.SCRIPT_SRC).get("chars"), is(22)); // 'self' 'report-sample'
    }

    @TestExtension("withContributors")
    public static class TestContributor implements Contributor {
        @Override
        public void apply(CspBuilder cspBuilder) {
            cspBuilder.add(Directive.IMG_SRC, "img.example.com");
        }
    }
}
