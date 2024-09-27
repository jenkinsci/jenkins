package hudson.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import jenkins.security.HMACConfidentialKey;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.jvnet.hudson.test.FlagRule;

public class HMACConfidentialKeyFIPSTest {
   @ClassRule
   // do not use the FIPS140 class here as that initializes the field before we set the property!
   public static TestRule flagRule = FlagRule.systemProperty("jenkins.security.FIPS140.COMPLIANCE", "true");

    @Test
    public void testTruncatedMacOnFips() {
        HMACConfidentialKey key1 = new HMACConfidentialKey("test", 16);
        IllegalArgumentException  iae = assertThrows(IllegalArgumentException.class, () -> key1.mac("Hello World"));
        assertEquals("Supplied length can't be less than 32 on FIPS mode", iae.getMessage());
    }

    @Test
    public void testCompleteMacOnFips() {
        HMACConfidentialKey key1 = new HMACConfidentialKey("test", 32);
        String str = key1.mac("Hello World");
        String pattern = "[0-9A-Fa-f]{64}";
        assertThat(str, matchesPattern(pattern));
    }
}
