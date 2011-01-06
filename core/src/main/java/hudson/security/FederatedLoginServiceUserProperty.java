package hudson.security;

import hudson.model.UserProperty;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author Kohsuke Kawaguchi
 * @since 1.393
 * @see FederatedLoginService
 */
public class FederatedLoginServiceUserProperty extends UserProperty {
    protected final Set<String> identifiers;

    protected FederatedLoginServiceUserProperty(Collection<String> identifiers) {
        this.identifiers = new HashSet<String>(identifiers);
    }

    public boolean has(String identifier) {
        return identifiers.contains(identifier);
    }

    public Collection<String> getIdentifiers() {
        return Collections.unmodifiableSet(identifiers);
    }

    public synchronized void addIdentifier(String id) throws IOException {
        identifiers.add(id);
        user.save();
    }
}
