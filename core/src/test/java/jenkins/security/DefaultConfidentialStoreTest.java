package jenkins.security;

import hudson.FilePath;
import hudson.Functions;
import java.io.File;
import java.nio.charset.Charset;

import org.apache.commons.io.FileUtils;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DefaultConfidentialStoreTest {

    @Rule
    public TemporaryFolder tmpRule = new TemporaryFolder();

    @Test
    public void roundtrip() throws Exception {
        File tmp = new File(tmpRule.getRoot(), "tmp"); // let ConfidentialStore create a directory

        DefaultConfidentialStore store = new DefaultConfidentialStore(tmp);
        ConfidentialKey key = new ConfidentialKey("test") {};

        // basic roundtrip
        String str = "Hello world!";
        store.store(key, str.getBytes());
        assertEquals(str, new String(store.load(key)));

        // data storage should have some stuff
        assertTrue(new File(tmp, "test").exists());
        assertTrue(new File(tmp, "master.key").exists());

        assertThat(FileUtils.readFileToString(new File(tmp, "test"), Charset.defaultCharset()), not(containsString("Hello"))); // the data shouldn't be a plain text, obviously

        if (!Functions.isWindows()) {
            assertEquals(0700, new FilePath(tmp).mode() & 0777); // should be read only
        }

        // if the master key changes, we should gracefully fail to load the store
        new File(tmp, "master.key").delete();
        DefaultConfidentialStore store2 = new DefaultConfidentialStore(tmp);
        assertTrue(new File(tmp, "master.key").exists()); // we should have a new key now
        assertNull(store2.load(key));
    }

}
