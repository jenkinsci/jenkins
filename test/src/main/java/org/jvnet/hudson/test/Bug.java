package org.jvnet.hudson.test;

import java.lang.annotation.Documented;

/**
 * Marks a test case to a bug filed in the issue tracker.
 *
 * @author Kohsuke Kawaguchi
 */
@Documented
public @interface Bug {
    /**
     * Issue number.
     */
    int value();
}
