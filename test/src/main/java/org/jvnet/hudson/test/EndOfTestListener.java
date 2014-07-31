package org.jvnet.hudson.test;

import hudson.ExtensionPoint;

/**
 * Gets notified before the test completes to perform additional cleanup.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.520
 */
public interface EndOfTestListener extends ExtensionPoint {
    /**
     * Called for clean up.
     */
    void onTearDown() throws Exception;
}
