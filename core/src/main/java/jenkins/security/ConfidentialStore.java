package jenkins.security;

import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Lookup;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.util.Secret;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.MetaInfServices;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * The actual storage for the data held by {@link ConfidentialKey}s, and the holder
 * of the master secret.
 *
 * <p>
 * This class is only relevant for the implementers of {@link ConfidentialKey}s.
 * Most plugin code should interact with {@link ConfidentialKey}s.
 *
 * <p>
 * OEM distributions of Jenkins can provide a custom {@link ConfidentialStore} implementation
 * by writing a subclass, mark it with {@link MetaInfServices} annotation, package it as a Jenkins module,
 * and bundling it with the war file. This doesn't use {@link Extension} because some plugins
 * have been found to use {@link Secret} before we get to {@link InitMilestone#PLUGINS_PREPARED}, and
 * therefore {@link Extension}s aren't loaded yet. (Similarly, it's conceivable that some future
 * core code might need this early on during the boot sequence.)
 *
 * @author Kohsuke Kawaguchi
 * @since 1.498
 */
public abstract class ConfidentialStore {
    /**
     * Persists the payload of {@link ConfidentialKey} to a persisted storage (such as disk.)
     * The expectation is that the persisted form is secure.
     */
    protected abstract void store(ConfidentialKey key, byte[] payload) throws IOException;

    /**
     * Reverse operation of {@link #store(ConfidentialKey, byte[])}
     *
     * @return
     *      null the data has not been previously persisted, or if the data was tampered.
     */
    protected abstract @CheckForNull byte[] load(ConfidentialKey key) throws IOException;

    // TODO consider promoting to public, and offering a default implementation of randomBytes (via the usual Util.isOverridden binary compat trick)
    abstract SecureRandom secureRandom();

    /**
     * Works like {@link SecureRandom#nextBytes(byte[])}.
     *
     * This enables implementations to consult other entropy sources, if it's available.
     */
    public abstract byte[] randomBytes(int size);

    /**
     * Retrieves the currently active singleton instance of {@link ConfidentialStore}.
     */
    public static @NonNull ConfidentialStore get() {
        Jenkins j = Jenkins.getInstanceOrNull();
        if (j == null) {
            return Mock.INSTANCE;
        }
        Lookup lookup = j.lookup;
        ConfidentialStore cs = lookup.get(ConfidentialStore.class);
        if (cs == null) {
            try {
                Iterator<ConfidentialStore> it = ServiceLoader.load(ConfidentialStore.class, ConfidentialStore.class.getClassLoader()).iterator();
                if (it.hasNext()) {
                    cs = it.next();
                }
            } catch (ServiceConfigurationError e) {
                LOGGER.log(Level.WARNING, "Failed to list up ConfidentialStore implementations", e);
                // fall through
            }

            if (cs == null)
                try {
                    cs = new DefaultConfidentialStore();
                } catch (Exception e) {
                    // if it's still null, bail out
                    throw new Error(e);
                }

            cs = lookup.setIfNull(ConfidentialStore.class, cs);
        }
        return cs;
    }

    @Restricted(NoExternalUse.class)
    @VisibleForTesting
    public static final class Mock extends ConfidentialStore {

        public static final Mock INSTANCE = new Mock();

        private final SecureRandom rand;

        private final Map<String, byte[]> data = new ConcurrentHashMap<>();

        Mock() {
            // Use a predictable seed to make tests more reproducible.
            try {
                rand = SecureRandom.getInstance("SHA1PRNG");
            } catch (NoSuchAlgorithmException x) {
                throw new AssertionError("https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#SecureRandom", x);
            }
            rand.setSeed(new byte[] {1, 2, 3, 4});
        }

        public void clear() {
            data.clear();
        }

        @Override
        protected void store(ConfidentialKey key, byte[] payload) throws IOException {
            //called only from tests, get hex string of sha 256 for logging payload
            LOGGER.fine("storing " + key.getId() + " " + Util.getHexOfSHA256DigestOf(payload));
            data.put(key.getId(), payload);
        }

        @Override
        protected byte[] load(ConfidentialKey key) throws IOException {
            byte[] payload = data.get(key.getId());
            //called only from tests, get hex string of sha 256 for logging payload
            LOGGER.fine("loading " + key.getId() + " " + (payload != null ? Util.getHexOfSHA256DigestOf(payload) : "null"));
            return payload;
        }

        @Override
        SecureRandom secureRandom() {
            return rand;
        }

        @Override
        public byte[] randomBytes(int size) {
            byte[] random = new byte[size];
            rand.nextBytes(random);
            return random;
        }

    }

    private static final Logger LOGGER = Logger.getLogger(ConfidentialStore.class.getName());
}
