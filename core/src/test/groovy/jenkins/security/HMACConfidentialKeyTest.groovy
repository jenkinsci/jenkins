package jenkins.security

import org.junit.Rule
import org.junit.Test

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class HMACConfidentialKeyTest {
    @Rule
    public ConfidentialStoreRule store = new ConfidentialStoreRule()

    def key = new HMACConfidentialKey("test",16)

    @Test
    void basics() {
        def unique = [] as TreeSet
        ["Hello world","","\u0000"].each { str ->
            def mac = key.mac(str)
            unique.add(mac)
            assert mac =~ /[0-9A-Fa-f]{32}/
            assert key.checkMac(str,mac)
            assert !key.checkMac("garbage",mac)
        }

        assert unique.size()==3 // make sure all 3 MAC are different
    }

    @Test
    void loadingExistingKey() {
        // this second key of the same ID will cause it to load the key from the disk
        def key2 = new HMACConfidentialKey("test",16)
        ["Hello world","","\u0000"].each { str ->
            assert key.mac(str)==key2.mac(str)
        }
    }
}
