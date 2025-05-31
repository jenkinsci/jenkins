package hudson.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.ExtensionList;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import jenkins.security.SecurityListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class HudsonPrivateSecurityRealmSEC2566Test {

    private HudsonPrivateSecurityRealmTest.SpySecurityListenerImpl spySecurityListener;

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        j = rule;
        spySecurityListener = ExtensionList.lookup(SecurityListener.class).get(HudsonPrivateSecurityRealmTest.SpySecurityListenerImpl.class);

        Field field = HudsonPrivateSecurityRealm.class.getDeclaredField("ID_REGEX");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test
    @Issue("SECURITY-2566")
    @Disabled("too fragile to run")
    void noTimingDifferenceForInternalSecurityRealm() throws Exception {
        final HudsonPrivateSecurityRealm realm = new HudsonPrivateSecurityRealm(false, false, null);
        j.jenkins.setSecurityRealm(realm);
        realm.createAccount("admin", "admin");
        final FullControlOnceLoggedInAuthorizationStrategy a = new FullControlOnceLoggedInAuthorizationStrategy();
        a.setAllowAnonymousRead(false);
        j.jenkins.setAuthorizationStrategy(a);

        final URL url = j.getURL();

        long[] correctUserTimings = new long[20];
        long[] incorrectUserTimings = new long[20];

        { // Authenticate with correct user, incorrect password
            for (int i = 0; i < correctUserTimings.length; i++) {
                final URLConnection urlConnection = url.openConnection();
                urlConnection.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString("admin:wrong".getBytes(StandardCharsets.UTF_8)));
                long start = System.nanoTime();
                try {
                    urlConnection.getContent(); // send request
                } catch (Exception ex) {
                    // don't care
                }
                long end = System.nanoTime();
                correctUserTimings[i] = end - start;
            }
        }

        { // Authenticate with wrong user
            for (int i = 0; i < incorrectUserTimings.length; i++) {
                final URLConnection urlConnection = url.openConnection();
                urlConnection.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString("wrong:wrong".getBytes(StandardCharsets.UTF_8)));
                long start = System.nanoTime();
                try {
                    urlConnection.getContent(); // send request
                } catch (Exception ex) {
                    // don't care
                }
                long end = System.nanoTime();
                incorrectUserTimings[i] = end - start;
            }
        }

        // Compute the averages, ignoring the 2 fastest and slowest times in an attempt to weed out outliers
        double incorrectAvg = Arrays.stream(incorrectUserTimings).sorted().skip(2).limit(16).average().orElse(0.0);
        double correctAvg = Arrays.stream(correctUserTimings).sorted().skip(2).limit(16).average().orElse(0.0);
        // expect roughly the same average times
        assertEquals(correctAvg, incorrectAvg, correctAvg * 0.1);
    }
}
