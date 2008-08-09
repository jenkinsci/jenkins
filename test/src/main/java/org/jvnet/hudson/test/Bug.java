package org.jvnet.hudson.test;

/**
 * Marks a test case to a bug filed in the issue tracker.
 *
 * @author Kohsuke Kawaguchi
 */
public @interface Bug {
    /**
     * Issue number.
     */
    int value();
}
