package jenkins.security;

import hudson.Extension;
import hudson.Lookup;
import hudson.init.InitMilestone;
import hudson.util.Secret;
import hudson.util.Service;
import jenkins.model.Jenkins;
import org.kohsuke.MetaInfServices;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    /**
     * Works like {@link SecureRandom#nextBytes(byte[])}.
     *
     * This enables implementations to consult other entropy sources, if it's available.
     */
    public abstract byte[] randomBytes(int size);

    /**
     * Retrieves the currently active singleton instance of {@link ConfidentialStore}.
     */
    public static @Nonnull ConfidentialStore get() {
        if (TEST!=null) return TEST.get();

        Jenkins j = Jenkins.getInstance();
        if (j == null) {
            throw new IllegalStateException("cannot initialize confidential key store until Jenkins has started");
        }
        Lookup lookup = j.lookup;
        ConfidentialStore cs = lookup.get(ConfidentialStore.class);
        if (cs==null) {
            try {
                List<ConfidentialStore> r = (List) Service.loadInstances(ConfidentialStore.class.getClassLoader(), ConfidentialStore.class);
                if (!r.isEmpty())
                    cs = r.get(0);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to list up ConfidentialStore implementations",e);
                // fall through
            }

            if (cs==null)
                try {
                    cs = new DefaultConfidentialStore();
                } catch (Exception e) {
                    // if it's still null, bail out
                    throw new Error(e);
                }

            cs = lookup.setIfNull(ConfidentialStore.class,cs);
        }
        return cs;
    }

    /**
     * Testing only. Used for testing {@link ConfidentialKey} without {@link Jenkins}
     */
    /*package*/ static ThreadLocal<ConfidentialStore> TEST = null;

    private static final Logger LOGGER = Logger.getLogger(ConfidentialStore.class.getName());
}
