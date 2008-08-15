package org.jvnet.hudson.test;

import java.lang.annotation.Documented;

/**
 * Marks a test case to a bug reported in the mailing list.
 *
 * @author Kohsuke Kawaguchi
 */
@Documented
public @interface Email {
    /**
     * URL to the e-mail archive.
     *
     * Look for the e-mail in
     * <a href="http://www.nabble.com/Hudson-users-f16872.html">http://www.nabble.com/Hudson-users-f16872.html</a> or
     * <a href="http://www.nabble.com/Hudson-dev-f25543.html">http://www.nabble.com/Hudson-dev-f25543.html</a>
     */
    String value();
}
