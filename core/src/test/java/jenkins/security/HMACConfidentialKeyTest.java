package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HMACConfidentialKeyTest {

    private HMACConfidentialKey key = new HMACConfidentialKey("test", 16);

    @BeforeEach
    void setUp() {
        ConfidentialStore.Mock.INSTANCE.clear();
    }

    @Test
    void basics() {
        Set<String> unique = new TreeSet<>();
        for (String str : new String[] {"Hello world", "", "\u0000"}) {
            String mac = key.mac(str);
            unique.add(mac);
            assertTrue(mac.matches("[0-9A-Fa-f]{32}"), mac);
            assertTrue(key.checkMac(str, mac));
            assertFalse(key.checkMac("garbage", mac));
        }
        assertEquals(3, unique.size(), "all 3 MAC are different");
    }

    @Test
    void loadingExistingKey() {
        // this second key of the same ID will cause it to load the key from the disk
        HMACConfidentialKey key2 = new HMACConfidentialKey("test", 16);
        for (String str : new String[] {"Hello world", "", "\u0000"}) {
            assertEquals(key.mac(str), key2.mac(str));
        }
    }

    @Test
    void testTruncatedMacOnNonFips() {
        HMACConfidentialKey key1 = new HMACConfidentialKey("test", 16);
        String str = key1.mac("Hello World");
        String pattern = "[0-9A-Fa-f]{32}";
        assertThat(str, matchesPattern(pattern));
    }
}
