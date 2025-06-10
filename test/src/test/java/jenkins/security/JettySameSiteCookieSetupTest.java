package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

@RunWith(Parameterized.class)
public class JettySameSiteCookieSetupTest {

    private static final Map<String, String> FLAG_TO_SAMESITE_COOKIE = new HashMap<>() {{
        put("", null);
        put("strict", "strict");
        put("lax", "lax");
        put(null, "lax");
    }};

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public FlagRule<String> sameSiteCookie;

    private final String sameSiteValue;

    public JettySameSiteCookieSetupTest(String sameSiteValue) {
        this.sameSiteValue = sameSiteValue;
        sameSiteCookie = FlagRule.systemProperty(JettySameSiteCookieSetup.class.getName() + ".sameSiteDefault", sameSiteValue);
    }

    @Parameterized.Parameters
    public static Set<String> sameSite() {
        return FLAG_TO_SAMESITE_COOKIE.keySet();
    }

    @Test
    public void testJettyFlagSetsSameSiteCookieProperty() throws Exception {
        String expected = FLAG_TO_SAMESITE_COOKIE.get(this.sameSiteValue);
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("admin"));

        try (JenkinsRule.WebClient wc = j.createWebClient()) {
            wc.login("admin", "admin", true);

            assertThat(wc.getCookieManager().getCookie("JSESSIONID").getSameSite(), is(expected));
            assertThat(wc.getCookieManager().getCookie("remember-me").getSameSite(), is(expected));
        }
    }
}
