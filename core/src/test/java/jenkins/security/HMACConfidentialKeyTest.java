package jenkins.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Set;
import java.util.TreeSet;
import org.junit.Rule;
import org.junit.Test;

public class HMACConfidentialKeyTest {

    @Rule
    public ConfidentialStoreRule store = new ConfidentialStoreRule();

    private HMACConfidentialKey key = new HMACConfidentialKey("test", 16);

    @Test
    public void basics() {
        Set<String> unique = new TreeSet<>();
        for (String str : new String[] {"Hello world", "", "\u0000"}) {
            String mac = key.mac(str);
            unique.add(mac);
            assertTrue(mac, mac.matches("[0-9A-Fa-f]{32}"));
            assertTrue(key.checkMac(str, mac));
            assertFalse(key.checkMac("garbage", mac));
        }
        assertEquals("all 3 MAC are different", 3, unique.size());
    }

    @Test
    public void loadingExistingKey() {
        // this second key of the same ID will cause it to load the key from the disk
        HMACConfidentialKey key2 = new HMACConfidentialKey("test", 16);
        for (String str : new String[] {"Hello world", "", "\u0000"}) {
            assertEquals(key.mac(str), key2.mac(str));
        }
    }

    @Test
    public void testTruncatedMacOnNonFips() {
        HMACConfidentialKey key1 = new HMACConfidentialKey("test", 16);
        String str = key1.mac("Hello World");
        String pattern = "[0-9A-Fa-f]{32}";
        assertThat(str, matchesPattern(pattern));
    }
}
