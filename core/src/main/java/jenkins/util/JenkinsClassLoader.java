package jenkins.util;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import jenkins.ClassLoaderReflectionToolkit;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Public versions of various {@link ClassLoader} methods for use in {@link
 * ClassLoaderReflectionToolkit}.
 */
@Restricted(NoExternalUse.class)
public interface JenkinsClassLoader {
    /** Public version of {@link ClassLoader#findClass(String)} */
    Class<?> findClass(String name) throws ClassNotFoundException;

    /** Public version of {@link ClassLoader#findLoadedClass(String)} */
    Class<?> findLoadedClass2(String name);

    /** Public version of {@link ClassLoader#findResource(String)} */
    URL findResource(String name);

    /** Public version of {@link ClassLoader#findResources(String)} */
    Enumeration<URL> findResources(String name) throws IOException;

    /** Public version of {@link ClassLoader#getClassLoadingLock(String)} */
    Object getClassLoadingLock(String className);
}
