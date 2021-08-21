package jenkins.security;

import org.junit.rules.ExternalResource;


/**
 * Test rule that makes {@link ConfidentialStore#get} be reset for each test.
 */
public class ConfidentialStoreRule extends ExternalResource {

    @Override
    protected void before() throws Throwable {
        ConfidentialStore.Mock.INSTANCE.clear();
    }

}
