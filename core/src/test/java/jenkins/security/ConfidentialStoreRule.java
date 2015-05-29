package jenkins.security;

import org.junit.rules.ExternalResource;

import org.junit.rules.TemporaryFolder;

/**
 * Test rule that injects a temporary {@link DefaultConfidentialStore}
 * @author Kohsuke Kawaguchi
 */
public class ConfidentialStoreRule extends ExternalResource {
    private final TemporaryFolder tmp = new TemporaryFolder();

    @Override
    protected void before() throws Throwable {
        tmp.create();
        ConfidentialStore.TEST.set(new DefaultConfidentialStore(tmp.getRoot()));
    }

    @Override
    protected void after() {
        ConfidentialStore.TEST.set(null);
        tmp.delete();
    }

    static {
        ConfidentialStore.TEST = new ThreadLocal<ConfidentialStore>();
    }
}
