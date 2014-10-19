package jenkins.security.s2m;

import jenkins.ReflectiveFilePathFilter;

import java.io.File;

/**
 * Tests a match against file operation name of {@link ReflectiveFilePathFilter#op(String, File)}.
 *
 * @author Kohsuke Kawaguchi
 */
interface OpMatcher {
    boolean matches(String op);

    OpMatcher ALL = new OpMatcher() {
        @Override
        public boolean matches(String op) {
            return true;
        }
    };
}
