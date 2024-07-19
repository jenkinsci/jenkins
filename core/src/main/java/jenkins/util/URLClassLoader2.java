package jenkins.util;

import java.net.URL;
import java.net.URLClassLoader;
import jenkins.ClassLoaderReflectionToolkit;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * {@link URLClassLoader} with loosened visibility for use with {@link
 * ClassLoaderReflectionToolkit}.
 */
@Restricted(NoExternalUse.class)
public class URLClassLoader2 extends URLClassLoader implements JenkinsClassLoader {

    static {
        registerAsParallelCapable();
    }

    /**
     * @deprecated use {@link URLClassLoader2#URLClassLoader2(String, URL[])}
     */
    @Deprecated(since = "2.459")
    public URLClassLoader2(URL[] urls) {
        super(urls);
    }

    /**
     * @deprecated use {@link URLClassLoader2#URLClassLoader2(String, URL[], ClassLoader)}
     */
    @Deprecated(since = "2.459")
    public URLClassLoader2(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    /**
     * Create a new {@link URLClassLoader2} with the given name and URLS and the {@link #getSystemClassLoader()} as its parent.
     * @param name name of this classloader.
     * @param urls the list of URLS to find classes in.
     * @since 2.459
     */
    public URLClassLoader2(String name, URL[] urls) {
        super(name, urls, getSystemClassLoader());
    }

    /**
     *  Create a new {@link URLClassLoader2} with the given name, URLS parent.
     * @param name name of this classloader.
     * @param urls the list of URLS to find classes in.
     * @param parent the parent to search for classes before we look in the {@code urls}
     * @since 2.459
     */
    public URLClassLoader2(String name, URL[] urls, ClassLoader parent) {
        super(name, urls, parent);
    }

    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        return super.findClass(name);
    }

    @Override
    public Class<?> findLoadedClass2(String name) {
        return super.findLoadedClass(name);
    }

    @Override
    public Object getClassLoadingLock(String className) {
        return super.getClassLoadingLock(className);
    }
}
