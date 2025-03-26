package jenkins.security;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.Util;
import hudson.util.Secret;
import hudson.util.TextFile;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;

/**
 * Default portable implementation of {@link ConfidentialStore} that uses
 * a directory inside $JENKINS_HOME.
 * <p>
 * The master key is stored by default in <code>$JENKINS_HOME/secrets/master.key</code> but another location can be provided using the system property <code>jenkins.security.DefaultConfidentialStore.file</code>.
 * <p>
 * It is also possible to prevent the generation of the master key file using the system property <code>-Djenkins.security.DefaultConfidentialStore.file.readOnly</code>.
 * In this case, the master key file must be provided or startup will fail.
 *
 * @author Kohsuke Kawaguchi
 */
// @MetaInfServices --- not annotated because this is the fallback implementation
public class DefaultConfidentialStore extends ConfidentialStore {
    static final String MASTER_KEY_FILE_SYSTEM_PROPERTY = DefaultConfidentialStore.class.getName() + ".file";
    static final String MASTER_KEY_READONLY_SYSTEM_PROPERTY_NAME = DefaultConfidentialStore.class.getName() + ".readOnly";

    private final SecureRandom sr = new SecureRandom();

    @NonNull
    private static File getMasterKeyFile(File rootDir) {
        var jenkinsMasterKey = SystemProperties.getString(MASTER_KEY_FILE_SYSTEM_PROPERTY);
        if (jenkinsMasterKey != null) {
            return new File(jenkinsMasterKey);
        } else {
            return new File(rootDir, "master.key");
        }
    }

    /**
     * Directory that stores individual keys.
     */
    private final File rootDir;

    /**
     * The master key.
     *
     * The sole purpose of the master key is to encrypt individual keys on the disk.
     * Because leaking this master key compromises all the individual keys, we must not let
     * this master key used for any other purpose, hence the protected access.
     */
    private final SecretKey masterKey;

    public DefaultConfidentialStore() throws IOException, InterruptedException {
        this(new File(Jenkins.get().getRootDir(), "secrets"));
    }

    public DefaultConfidentialStore(File rootDir) throws IOException, InterruptedException {
        this(rootDir, getMasterKeyFile(rootDir));
    }

    protected DefaultConfidentialStore(File rootDir, File keyFile) throws IOException, InterruptedException {
        this.rootDir = rootDir;
        if (rootDir.mkdirs()) {
            // protect this directory. but don't change the permission of the existing directory
            // in case the administrator changed this.
            new FilePath(rootDir).chmod(0700);
        }

        TextFile masterSecret = new TextFile(keyFile);
        if (!masterSecret.exists()) {
            if (SystemProperties.getBoolean(MASTER_KEY_READONLY_SYSTEM_PROPERTY_NAME)) {
                throw new IOException(masterSecret + " does not exist and system property " + MASTER_KEY_READONLY_SYSTEM_PROPERTY_NAME + " is set. You must provide a valid master key file.");
            } else {
                // we are only going to use small number of bits (since export control limits AES key length)
                // but let's generate a long enough key anyway
                masterSecret.write(Util.toHexString(randomBytes(128)));
            }
        }
        this.masterKey = Util.toAes128Key(masterSecret.readTrim());
    }

    /**
     * Persists the payload of {@link ConfidentialKey} to the disk.
     */
    @Override
    protected void store(ConfidentialKey key, byte[] payload) throws IOException {
        try {
            Cipher sym = Secret.getCipher("AES");
            sym.init(Cipher.ENCRYPT_MODE, masterKey);
            try (OutputStream fos = Files.newOutputStream(getFileFor(key).toPath());
                 CipherOutputStream cos = new CipherOutputStream(fos, sym)) {
                cos.write(payload);
                cos.write(MAGIC);
            }
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to persist the key: " + key.getId(), e);
        } catch (InvalidPathException e) {
            throw new IOException(e);
        }
    }

    /**
     * Reverse operation of {@link #store(ConfidentialKey, byte[])}
     *
     * @return
     *      null the data has not been previously persisted.
     */
    @Override
    protected byte[] load(ConfidentialKey key) throws IOException {
        try {
            File f = getFileFor(key);
            if (!f.exists())    return null;

            Cipher sym = Secret.getCipher("AES");
            sym.init(Cipher.DECRYPT_MODE, masterKey);
            try (InputStream fis = Files.newInputStream(f.toPath());
                 CipherInputStream cis = new CipherInputStream(fis, sym)) {
                byte[] bytes = cis.readAllBytes();
                return verifyMagic(bytes);
            }
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to load the key: " + key.getId(), e);
        } catch (InvalidPathException e) {
            throw new IOException(e);
        } catch (IOException x) {
            if (x.getCause() instanceof BadPaddingException) {
                return null; // broken somehow
            } else {
                throw x;
            }
        }
    }

    /**
     * Verifies that the given byte[] has the MAGIC trailer, to verify the integrity of the decryption process.
     */
    private byte[] verifyMagic(byte[] payload) {
        int payloadLen = payload.length - MAGIC.length;
        if (payloadLen < 0)   return null;    // obviously broken

        for (int i = 0; i < MAGIC.length; i++) {
            if (payload[payloadLen + i] != MAGIC[i])
                return null;    // broken
        }
        byte[] truncated = new byte[payloadLen];
        System.arraycopy(payload, 0, truncated, 0, truncated.length);
        return truncated;
    }

    private File getFileFor(ConfidentialKey key) {
        return new File(rootDir, key.getId());
    }

    @Override
    SecureRandom secureRandom() {
        return sr;
    }

    @Override
    public byte[] randomBytes(int size) {
        byte[] random = new byte[size];
        sr.nextBytes(random);
        return random;
    }

    private static final byte[] MAGIC = "::::MAGIC::::".getBytes(StandardCharsets.US_ASCII);
}
