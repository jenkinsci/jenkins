package org.jvnet.hudson.test;

/**
 * @author Kohsuke Kawaguchi
 */
public class TestEnvironment {
    public final TemporaryDirectoryAllocator temporaryDirectoryAllocator = new TemporaryDirectoryAllocator();

    public void pin() {
        ENVIRONMENT.set(this);
    }

    public void dispose() {
        ENVIRONMENT.set(null);
        temporaryDirectoryAllocator.disposeAsync();
    }

    public static final ThreadLocal<TestEnvironment> ENVIRONMENT = new InheritableThreadLocal<TestEnvironment>();

    public static TestEnvironment get() {
        return ENVIRONMENT.get();
    }
}
