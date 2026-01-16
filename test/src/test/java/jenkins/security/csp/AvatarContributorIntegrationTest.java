package jenkins.security.csp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class AvatarContributorIntegrationTest {

    @Test
    @SuppressWarnings("deprecation")
    void allowUrlIsAddedToCspImgSrc(JenkinsRule r) {
        if (r.jenkins.getExtensionList(AvatarContributor.class).isEmpty()) {
            r.jenkins.getExtensionList(AvatarContributor.class).add(new AvatarContributor());
        }

        String url = "https://example.com/avatars/user.png";
        AvatarContributor.allowUrl(url);
        CspBuilder builder = new CspBuilder().withDefaultContributions();
        String csp = builder.build();
        assertThat(csp, containsString("https://example.com/avatars/user.png"));
    }

    @Test
    @SuppressWarnings("deprecation")
    void invalidUrlIsNotAddedToCspImgSrc(JenkinsRule r) {
        if (r.jenkins.getExtensionList(AvatarContributor.class).isEmpty()) {
            r.jenkins.getExtensionList(AvatarContributor.class).add(new AvatarContributor());
        }

        AvatarContributor.allowUrl("javascript:alert(1)");
        CspBuilder builder = new CspBuilder().withDefaultContributions();
        String csp = builder.build();
        assertThat(csp, not(containsString("javascript:alert(1)")));
    }
}
