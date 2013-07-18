package jenkins.security

import org.junit.Rule
import org.junit.Test

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class CryptoConfidentialKeyTest {
    @Rule
    public ConfidentialStoreRule store = new ConfidentialStoreRule()

    def key = new CryptoConfidentialKey("test")

    @Test
    void decryptGetsPlainTextBack() {
        ["Hello world","","\u0000"].each { str ->
            assert key.decrypt().doFinal(key.encrypt().doFinal(str.bytes))==str.bytes
        }
    }

    @Test
    void multipleEncryptsAreIdempotent() {
        def str = "Hello world".bytes
        assert key.encrypt().doFinal(str)==key.encrypt().doFinal(str)
    }

    @Test
    void loadingExistingKey() {
        def key2 = new CryptoConfidentialKey("test") // this will cause the key to be loaded from the disk
        ["Hello world","","\u0000"].each { str ->
            assert key2.decrypt().doFinal(key.encrypt().doFinal(str.bytes))==str.bytes
        }
    }
}
