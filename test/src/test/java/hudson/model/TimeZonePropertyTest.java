package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.Collections;
import java.util.TimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Test cases for TimeZoneProperty
 */
@WithJenkins
class TimeZonePropertyTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void testEnsureTimeZoneIsNullByDefault() {
        String timeZone = TimeZoneProperty.forCurrentUser();
        assertNull(timeZone);
    }

    @Test
    void testEnsureInvalidTimeZoneDefaultsToNull() throws IOException {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        User user = User.get("John Smith", true, Collections.emptyMap());
        SecurityContextHolder.getContext().setAuthentication(user.impersonate2());

        TimeZoneProperty tzp = new TimeZoneProperty("InvalidTimeZoneName");
        user.addProperty(tzp);

        assertNull(TimeZoneProperty.forCurrentUser());
    }

    @Test
    void testSetUserDefinedTimeZone() throws IOException {
        String timeZone = TimeZone.getDefault().getID();
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());

        User user = User.get("John Smith", true, Collections.emptyMap());
        SecurityContextHolder.getContext().setAuthentication(user.impersonate2());

        assertNull(TimeZoneProperty.forCurrentUser());
        TimeZoneProperty tzp = new TimeZoneProperty(timeZone);
        user.addProperty(tzp);
        assertEquals(TimeZone.getDefault().getID(), TimeZoneProperty.forCurrentUser());
    }
}
