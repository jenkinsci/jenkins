package hudson.maven;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * ClassLoader that delegates to multiple side classloaders, in addition to the parent.
 *
 * The side classloaders generally need to be carefully crafted
 * to avoid classloader constraint violations.
 *
 * @author Kohsuke Kawaguchi
 */
class AggregatingClassLoader extends ClassLoader {
    private final List<ClassLoader> sides;

    public AggregatingClassLoader(ClassLoader parent, List<ClassLoader> sides) {
        super(parent);
        this.sides = new ArrayList<ClassLoader>(sides);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        for (ClassLoader cl : sides) {
            try {
                return cl.loadClass(name);
            } catch (ClassNotFoundException e) {
                //not found. try next
            }
        }
        // not found in any of the classloader. delegate.
        throw new ClassNotFoundException(name);
    }

    @Override
    protected URL findResource(String name) {
        for (ClassLoader cl : sides) {
            URL url = cl.getResource(name);
            if (url!=null)
                return url;
        }
        return null;
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        List<URL> resources = new ArrayList<URL>();
        for (ClassLoader cl : sides) {
            resources.addAll(Collections.list(cl.getResources(name)));
        }
        return Collections.enumeration(resources);
    }
}
