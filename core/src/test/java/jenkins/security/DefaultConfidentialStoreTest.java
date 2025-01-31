package jenkins.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import hudson.FilePath;
import hudson.Functions;
import hudson.Util;
import hudson.util.TextFile;
import java.io.File;
import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DefaultConfidentialStoreTest {

    @Rule
    public TemporaryFolder tmpRule = new TemporaryFolder();

    private final SecureRandom sr = new SecureRandom();

    @Test
    public void roundtrip() throws Exception {
        File tmp = new File(tmpRule.getRoot(), "tmp"); // let ConfidentialStore create a directory

        DefaultConfidentialStore store = new DefaultConfidentialStore(tmp);
        ConfidentialKey key = new ConfidentialKey("test") {};

        assertTrue(new File(tmp, "master.key").exists());
        roundTrip(store, key, tmp);

        // if the master key changes, we should gracefully fail to load the store
        new File(tmp, "master.key").delete();
        DefaultConfidentialStore store2 = new DefaultConfidentialStore(tmp);
        assertTrue(new File(tmp, "master.key").exists()); // we should have a new key now
        assertNull(store2.load(key));
    }

    private static void roundTrip(DefaultConfidentialStore store, ConfidentialKey key, File tmp) throws IOException, InterruptedException {
        // basic roundtrip
        String str = "Hello world!";
        store.store(key, str.getBytes(StandardCharsets.UTF_8));
        assertEquals(str, new String(store.load(key), StandardCharsets.UTF_8));

        // data storage should have some stuff
        assertTrue(new File(tmp, "test").exists());

        assertThrows(MalformedInputException.class, () -> Files.readString(tmp.toPath().resolve("test"), StandardCharsets.UTF_8)); // the data shouldn't be a plain text, obviously

        if (!Functions.isWindows()) {
            assertEquals(0700, new FilePath(tmp).mode() & 0777); // should be read only
        }
    }

    @Test
    public void masterKeyGeneratedBeforehand() throws IOException, InterruptedException {
        File external = new File(tmpRule.getRoot(), "external");
        File tmp = new File(tmpRule.getRoot(), "tmp");
        var masterKeyFile = new File(external, "master.key");
        new TextFile(masterKeyFile).write(Util.toHexString(randomBytes(128)));
        System.setProperty(DefaultConfidentialStore.MASTER_KEY_FILE_SYSTEM_PROPERTY, masterKeyFile.getAbsolutePath());
        System.setProperty(DefaultConfidentialStore.MASTER_KEY_READONLY_SYSTEM_PROPERTY_NAME, "true");
        DefaultConfidentialStore store = new DefaultConfidentialStore(tmp);
        ConfidentialKey key = new ConfidentialKey("test") {};
        roundTrip(store, key, tmp);
        // With this configuration, the master key file deletion is fatal
        masterKeyFile.delete();
        assertThrows(IOException.class, () -> new DefaultConfidentialStore(tmp));
    }

    private byte[] randomBytes(int size) {
        byte[] random = new byte[size];
        sr.nextBytes(random);
        return random;
    }

}
