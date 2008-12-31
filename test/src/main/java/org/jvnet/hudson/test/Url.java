package org.jvnet.hudson.test;

import java.lang.annotation.Documented;

/**
 * Marks a test case to a bug reported in the other sources.
 *
 * @author Kohsuke Kawaguchi
 * @see Email
 */
@Documented
public @interface Url {
    /**
     * URL to the web page indicating a problem related to this test case.
     */
    String value();
}
