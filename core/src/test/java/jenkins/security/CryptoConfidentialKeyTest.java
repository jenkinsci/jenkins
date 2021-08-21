package jenkins.security;

import static org.junit.Assert.assertArrayEquals;
import org.junit.Rule;
import org.junit.Test;

public class CryptoConfidentialKeyTest {

    @Rule
    public ConfidentialStoreRule store = new ConfidentialStoreRule();

    private CryptoConfidentialKey key = new CryptoConfidentialKey("test");

    @Test
    public void decryptGetsPlainTextBack() throws Exception {
        for (String str : new String[] {"Hello world", "", "\u0000"}) {
            assertArrayEquals(str.getBytes(), key.decrypt().doFinal(key.encrypt().doFinal(str.getBytes())));
        }
    }

    @Test
    public void multipleEncryptsAreIdempotent() throws Exception {
        byte[] str = "Hello world".getBytes();
        assertArrayEquals(key.encrypt().doFinal(str), key.encrypt().doFinal(str));
    }

    @Test
    public void loadingExistingKey() throws Exception {
        CryptoConfidentialKey key2 = new CryptoConfidentialKey("test"); // this will cause the key to be loaded from the disk
        for (String str : new String[] {"Hello world", "", "\u0000"}) {
            assertArrayEquals(str.getBytes(), key2.decrypt().doFinal(key.encrypt().doFinal(str.getBytes())));
        }
    }

}
