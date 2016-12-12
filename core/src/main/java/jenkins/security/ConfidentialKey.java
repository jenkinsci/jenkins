package jenkins.security;

import hudson.scm.SCM;
import hudson.tasks.Builder;
import hudson.util.Secret;

import javax.annotation.CheckForNull;
import java.io.IOException;

/**
 * Confidential information that gets stored as a singleton in Jenkins, mostly some random token value.
 *
 * <p>
 * The actual value is persisted via {@link ConfidentialStore}, but each use case that requires
 * a secret like this should use a separate {@link ConfidentialKey} instance so that one compromised
 * {@link ConfidentialKey} (say through incorrect usage and failure to protect it) shouldn't compromise
 * all the others.
 *
 * <p>
 * {@link ConfidentialKey} is ultimately a sequence of bytes,
 * but for convenience, a family of subclasses are provided to represent the secret in different formats.
 * See {@link HexStringConfidentialKey} and {@link HMACConfidentialKey} for example. In addition to the programming
 * ease, these use case specific subtypes make it harder for vulnerability to creep in by making it harder
 * for the secret to leak.
 *
 * <p>
 * The {@link ConfidentialKey} subtypes are expected to be used as a singleton, like {@link JnlpSlaveAgentProtocol#SLAVE_SECRET}.
 * For code that relies on XStream for persistence (such as {@link Builder}s, {@link SCM}s, and other fragment objects
 * around builds and jobs), {@link Secret} provides more convenient way of storing secrets.
 *
 * @author Kohsuke Kawaguchi
 * @see Secret
 * @since 1.498
 */
public abstract class ConfidentialKey {
    /**
     * Name of the key. This is used as the file name.
     */
    private final String id;

    protected ConfidentialKey(String id) {
        this.id = id;
    }

    protected @CheckForNull byte[] load() throws IOException {
        return ConfidentialStore.get().load(this);
    }

    protected void store(byte[] payload) throws IOException {
        ConfidentialStore.get().store(this,payload);
    }

    public String getId() {
        return id;
    }
}
