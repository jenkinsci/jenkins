package jenkins.security.s2m;

import hudson.Util;
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.jenkinsci.remoting.RoleSensitive;

/**
 * Set of fully-qualified {@link RoleSensitive} (mostly Callable) class names that are whitelisted by admin.
 *
 * @author Kohsuke Kawaguchi
 */
class CallableWhitelistConfig extends ConfigDirectory<String,Set<String>> {
    CallableWhitelistConfig(File file) {
        super(file);
    }

    @Override
    protected Set<String> create() {
        return new HashSet<>();
    }

    @Override
    protected Set<String> readOnly(Set<String> base) {
        return Collections.unmodifiableSet(new HashSet<>(base));
    }

    @Override
    protected String parse(String line) {
        return Util.fixEmptyAndTrim(line);
    }

    public boolean contains(String name) {
        return get().contains(name);
    }
}
