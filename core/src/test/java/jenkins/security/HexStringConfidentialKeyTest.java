package jenkins.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HexStringConfidentialKeyTest {

    @BeforeEach
    void setUp() {
        ConfidentialStore.Mock.INSTANCE.clear();
    }

    @Test
    void hexStringShouldProduceHexString() {
        HexStringConfidentialKey key = new HexStringConfidentialKey("test", 8);
        assertTrue(key.get().matches("[A-Fa-f0-9]{8}"));
    }

    @Test
    void multipleGetsAreIdempotent() {
        HexStringConfidentialKey key = new HexStringConfidentialKey("test", 8);
        assertEquals(key.get(), key.get());
    }

    @Test
    void specifyLengthAndMakeSureItTakesEffect() {
        for (int n : new int[] {8, 16, 32, 256}) {
            assertEquals(n, new HexStringConfidentialKey("test" + n, n).get().length());
        }
    }

    @Test
    void loadingExistingKey() {
        HexStringConfidentialKey key1 = new HexStringConfidentialKey("test", 8);
        key1.get(); // this causes the ke to be generated

        // this second key of the same ID will cause it to load the key from the disk
        HexStringConfidentialKey key2 = new HexStringConfidentialKey("test", 8);
        assertEquals(key1.get(), key2.get());
    }

}
