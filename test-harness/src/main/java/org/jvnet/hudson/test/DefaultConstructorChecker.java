package org.jvnet.hudson.test;

import junit.framework.TestCase;

/**
 * Tests that the specified class has the default constructor.
 *
 * @author Kohsuke Kawaguchi
 */
public class DefaultConstructorChecker extends TestCase {
    private final Class clazz;

    public DefaultConstructorChecker(Class clazz) {
        this.clazz = clazz;
        setName(clazz.getName()+".verifyDefaultConstructor");
    }

    @Override
    protected void runTest() throws Throwable {
        try {
            clazz.getConstructor();
        } catch (NoSuchMethodException e) {
            throw new Error(clazz+" must have the default constructor",e);
        } catch (SecurityException e) {
            throw new Error(clazz+" must have the default constructor",e);
        }
    }
}
