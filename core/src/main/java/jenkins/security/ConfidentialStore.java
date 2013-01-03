package jenkins.security;

import hudson.Extension;
import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.SecureRandom;

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
 * by writing a subclass, mark it with {@link Extension} annotation, package it as a Jenkins module,
 * and bundling it with the war file.
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
        return Jenkins.getInstance().getExtensionList(ConfidentialStore.class).get(0);
    }

    /**
     * Testing only. Used for testing {@link ConfidentialKey} without {@link Jenkins}
     */
    /*package*/ static ThreadLocal<ConfidentialStore> TEST = null;
}
