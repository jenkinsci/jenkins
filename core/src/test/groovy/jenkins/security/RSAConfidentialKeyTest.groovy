package jenkins.security

import org.junit.Rule
import org.junit.Test

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class RSAConfidentialKeyTest {
    @Rule
    public ConfidentialStoreRule store = new ConfidentialStoreRule()

    def key = new RSAConfidentialKey("test") {}

    @Test
    void loadingExistingKey() {
        // this second key of the same ID will cause it to load the key from the disk
        def key2 = new RSAConfidentialKey("test") {}

        assert key.privateKey==key2.privateKey;
        assert key.publicKey ==key2.publicKey;
    }
}
