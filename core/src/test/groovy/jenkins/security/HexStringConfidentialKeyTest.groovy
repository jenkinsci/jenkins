package jenkins.security

import org.junit.Rule
import org.junit.Test

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class HexStringConfidentialKeyTest {
    @Rule
    public ConfidentialStoreRule store = new ConfidentialStoreRule()

    @Test
    void hexStringShouldProduceHexString() {
        def key = new HexStringConfidentialKey("test",8)
        assert key.get() =~ /[A-Fa-f0-9]{8}/
    }

    @Test
    void multipleGetsAreIdempotent() {
        def key = new HexStringConfidentialKey("test",8)
        assert key.get()==key.get()
    }

    @Test
    void specifyLengthAndMakeSureItTakesEffect() {
        [8,16,32,256].each { n ->
            new HexStringConfidentialKey("test"+n,n).get().length()==n
        }
    }

    @Test
    void loadingExistingKey() {
        def key1 = new HexStringConfidentialKey("test",8)
        key1.get() // this causes the ke to be generated

        // this second key of the same ID will cause it to load the key from the disk
        def key2 = new HexStringConfidentialKey("test",8)
        assert key1.get()==key2.get()
    }
}
