package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@ParameterizedClass
@MethodSource("sameSite")
class JettySameSiteCookieSetupTest {

    private static final Map<String, String> FLAG_TO_SAMESITE_COOKIE = new HashMap<>() {{
        put("", null);
        put("strict", "strict");
        put("lax", "lax");
        put(null, "lax");
    }};

    @RegisterExtension
    private final JenkinsSessionExtension session = new JenkinsSessionExtension();

    private String sameSiteCookie;

    @Parameter
    private String sameSiteValue;

    static Set<String> sameSite() {
        return FLAG_TO_SAMESITE_COOKIE.keySet();
    }

    @BeforeEach
    void setUp() {
        if (sameSiteValue != null) {
            sameSiteCookie = System.setProperty(JettySameSiteCookieSetup.class.getName() + ".sameSiteDefault", sameSiteValue);
        } else {
            sameSiteCookie = System.clearProperty(JettySameSiteCookieSetup.class.getName() + ".sameSiteDefault");
        }
    }

    @AfterEach
    void tearDown() {
        if (sameSiteCookie != null) {
            System.setProperty(JettySameSiteCookieSetup.class.getName() + ".sameSiteDefault", sameSiteCookie);
        } else {
            System.clearProperty(JettySameSiteCookieSetup.class.getName() + ".sameSiteDefault");
        }
    }

    @Test
    void testJettyFlagSetsSameSiteCookieProperty() throws Throwable {
        String expected = FLAG_TO_SAMESITE_COOKIE.get(sameSiteValue);
        session.then(j -> {
            j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
            j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().grant(Jenkins.ADMINISTER).everywhere().to("admin"));

            try (JenkinsRule.WebClient wc = j.createWebClient()) {
                wc.login("admin", "admin", true);

                assertThat(wc.getCookieManager().getCookie("JSESSIONID").getSameSite(), is(expected));
                assertThat(wc.getCookieManager().getCookie("remember-me").getSameSite(), is(expected));
            }
        });
    }
}
