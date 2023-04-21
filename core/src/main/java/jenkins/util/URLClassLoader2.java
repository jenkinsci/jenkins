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

    public URLClassLoader2(URL[] urls) {
        super(urls);
    }

    public URLClassLoader2(URL[] urls, ClassLoader parent) {
        super(urls, parent);
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
