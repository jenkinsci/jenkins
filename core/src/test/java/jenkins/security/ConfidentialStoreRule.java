package jenkins.security;

import hudson.Util;
import org.junit.rules.ExternalResource;

import java.io.File;
import java.io.IOException;

/**
 * Test rule that injects a temporary {@link DefaultConfidentialStore}
 * @author Kohsuke Kawaguchi
 */
public class ConfidentialStoreRule extends ExternalResource {
    public ConfidentialStore store;
    public File tmp;

    @Override
    protected void before() throws Throwable {
        tmp = Util.createTempDir();
        store = new DefaultConfidentialStore(tmp);
        ConfidentialStore.TEST.set(store);
    }

    @Override
    protected void after() {
        ConfidentialStore.TEST.set(null);
        try {
            Util.deleteRecursive(tmp);
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    static {
        ConfidentialStore.TEST = new ThreadLocal<ConfidentialStore>();
    }
}
