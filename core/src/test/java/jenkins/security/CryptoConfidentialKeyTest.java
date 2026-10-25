package jenkins.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CryptoConfidentialKeyTest {

    private CryptoConfidentialKey key = new CryptoConfidentialKey("test");

    @BeforeEach
    void setUp() {
        ConfidentialStore.Mock.INSTANCE.clear();
    }

    @Test
    void decryptGetsPlainTextBack() throws Exception {
        for (String str : new String[] {"Hello world", "", "\u0000"}) {
            assertArrayEquals(str.getBytes(StandardCharsets.UTF_8), key.decrypt().doFinal(key.encrypt().doFinal(str.getBytes(StandardCharsets.UTF_8))));
        }
    }

    @Test
    void multipleEncryptsAreIdempotent() throws Exception {
        byte[] str = "Hello world".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(key.encrypt().doFinal(str), key.encrypt().doFinal(str));
    }

    @Test
    void loadingExistingKey() throws Exception {
        CryptoConfidentialKey key2 = new CryptoConfidentialKey("test"); // this will cause the key to be loaded from the disk
        for (String str : new String[] {"Hello world", "", "\u0000"}) {
            assertArrayEquals(str.getBytes(StandardCharsets.UTF_8), key2.decrypt().doFinal(key.encrypt().doFinal(str.getBytes(StandardCharsets.UTF_8))));
        }
    }

}
