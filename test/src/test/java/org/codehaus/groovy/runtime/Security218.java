package org.codehaus.groovy.runtime;

import org.jenkinsci.remoting.RoleChecker;

import java.io.Serializable;

/**
 * Test payload.
 *
 * @author Kohsuke Kawaguchi
 */
public class Security218 implements Serializable, hudson.remoting.Callable<Void,RuntimeException> {
    @Override
    public Void call() throws RuntimeException {
        return null;
    }

    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
    }
}
