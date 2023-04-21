package jenkins.security;

import static org.junit.Assert.assertArrayEquals;

import java.nio.charset.StandardCharsets;
import org.junit.Rule;
import org.junit.Test;

public class CryptoConfidentialKeyTest {

    @Rule
    public ConfidentialStoreRule store = new ConfidentialStoreRule();

    private CryptoConfidentialKey key = new CryptoConfidentialKey("test");

    @Test
    public void decryptGetsPlainTextBack() throws Exception {
        for (String str : new String[] {"Hello world", "", "\u0000"}) {
            assertArrayEquals(str.getBytes(StandardCharsets.UTF_8), key.decrypt().doFinal(key.encrypt().doFinal(str.getBytes(StandardCharsets.UTF_8))));
        }
    }

    @Test
    public void multipleEncryptsAreIdempotent() throws Exception {
        byte[] str = "Hello world".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(key.encrypt().doFinal(str), key.encrypt().doFinal(str));
    }

    @Test
    public void loadingExistingKey() throws Exception {
        CryptoConfidentialKey key2 = new CryptoConfidentialKey("test"); // this will cause the key to be loaded from the disk
        for (String str : new String[] {"Hello world", "", "\u0000"}) {
            assertArrayEquals(str.getBytes(StandardCharsets.UTF_8), key2.decrypt().doFinal(key.encrypt().doFinal(str.getBytes(StandardCharsets.UTF_8))));
        }
    }

}
