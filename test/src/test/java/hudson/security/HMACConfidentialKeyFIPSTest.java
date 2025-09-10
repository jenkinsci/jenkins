package hudson.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jenkins.security.HMACConfidentialKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HMACConfidentialKeyFIPSTest {
   // do not use the FIPS140 class here as that initializes the field before we set the property!
   private static String fips140;

   @BeforeAll
   static void setUp() {
       fips140 = System.setProperty("jenkins.security.FIPS140.COMPLIANCE", "true");
   }

   @AfterEach
   void tearDown() {
       if (fips140 != null) {
           System.setProperty("jenkins.security.FIPS140.COMPLIANCE", fips140);
       } else {
           System.clearProperty("jenkins.security.FIPS140.COMPLIANCE");
       }
   }

    @Test
    void testTruncatedMacOnFips() {
        HMACConfidentialKey key1 = new HMACConfidentialKey("test", 16);
        IllegalArgumentException  iae = assertThrows(IllegalArgumentException.class, () -> key1.mac("Hello World"));
        assertEquals("Supplied length can't be less than 32 on FIPS mode", iae.getMessage());
    }

    @Test
    void testCompleteMacOnFips() {
        HMACConfidentialKey key1 = new HMACConfidentialKey("test", 32);
        String str = key1.mac("Hello World");
        String pattern = "[0-9A-Fa-f]{64}";
        assertThat(str, matchesPattern(pattern));
    }
}
