package jenkins.security;

import org.apache.commons.collections.Transformer;
import org.codehaus.groovy.runtime.ConversionHandler;

import javax.xml.transform.Templates;

/**
 * Prevents problematic classes from getting de-serialized.
 *
 * @author Kohsuke Kawaguchi
 */
public class RemotingFilterClassLoader extends ClassLoader {
    private final ClassLoader actual;

    public RemotingFilterClassLoader(ClassLoader actual) {
        // intentionally not passing 'actual' as the parent classloader to the super type
        // to prevent accidental bypassing of a filter.
        this.actual = actual;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (isBlacklisted(name))    throw new ClassNotFoundException(name);
        Class<?> c = actual.loadClass(name);
        if (isBlacklisted(c))       throw new ClassNotFoundException(name);
        return c;
    }

    protected boolean isBlacklisted(String name) {
        return false;
    }

    protected boolean isBlacklisted(Class c) {
        if (Transformer.class.isAssignableFrom(c))
            return true;
        if (ConversionHandler.class.isAssignableFrom(c))
            return true;
        if (Templates.class.isAssignableFrom(c))
            return true;

        return false;
    }
}
