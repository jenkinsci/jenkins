package jenkins.security

import hudson.FilePath
import hudson.Functions
import hudson.Util
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * @author Kohsuke Kawaguchi
 */
public class DefaultConfidentialStoreTest {

    def tmp;

    @Before
    void setup() {
        tmp = Util.createTempDir()
    }

    @After
    void tearDown() {
        Util.deleteRecursive(tmp)
    }

    @Test
    void roundtrip() {
        tmp.deleteDir()   // let ConfidentialStore create a directory

        def store = new DefaultConfidentialStore(tmp);
        def key = new ConfidentialKey("test") {};

        // basic roundtrip
        def str = "Hello world!"
        store.store(key, str.bytes)
        assert new String(store.load(key))==str

        // data storage should have some stuff
        assert new File(tmp,"test").exists()
        assert new File(tmp,"master.key").exists()

        assert !new File(tmp,"test").text.contains("Hello") // the data shouldn't be a plain text, obviously

        if (!Functions.isWindows())
            assert (new FilePath(tmp).mode()&0777) == 0700 // should be read only

        // if the master key changes, we should gracefully fail to load the store
        new File(tmp,"master.key").delete()
        def store2 = new DefaultConfidentialStore(tmp)
        assert new File(tmp,"master.key").exists()  // we should have a new key now
        assert store2.load(key)==null;
    }
}
