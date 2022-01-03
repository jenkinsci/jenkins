package jenkins;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.RestrictedSince;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import jenkins.util.JenkinsClassLoader;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Reflective access to various {@link ClassLoader} methods which are otherwise {@code protected}.
 * <p>
 * Initially tries to access methods using known classloaders in use that expose the methods
 * to prevent illegal reflective access errors on Java 11+
 * Then falls back to accessing the {@link ClassLoader} methods.
 * <p>
 * All reflection method initialisation is delayed until first use so that we don't access the methods if we don't need to.
 * <p>
 * Note: Currently there is no known production use-case for the fallback case of accessing these methods via reflection:
 * the {@code JenkinsRule} tests use a different classloader,
 * but once that is made consistent with production Jenkins we can re-evaluate the fallback code.
 */
@Restricted(NoExternalUse.class)
@RestrictedSince("TODO")
public class ClassLoaderReflectionToolkit {

    private static <T extends Exception> Object invoke(Method method, Class<T> exception, Object obj, Object... args) throws T {
        try {
            return method.invoke(obj, args);
        } catch (IllegalAccessException x) {
            throw new LinkageError(x.getMessage(), x);
        } catch (InvocationTargetException x) {
            Throwable x2 = x.getCause();
            if (x2 instanceof RuntimeException) {
                throw (RuntimeException) x2;
            } else if (x2 instanceof Error) {
                throw (Error) x2;
            } else if (exception.isInstance(x2)) {
                throw exception.cast(x2);
            } else {
                throw new AssertionError(x2);
            }
        }
    }

    /**
     * Return the lock object for class loading operations.
     *
     * <p>If the {@link ClassLoader} object is registered as parallel capable, the method returns a
     * dedicated object associated with the specified class name. Otherwise, the method returns the
     * {@link ClassLoader} object.
     *
     * @param name The name of the to-be-loaded class.
     * @return The lock for class loading operations.
     * @throws NullPointerException If registered as parallel capable and {@code name} is {@code
     *     null}.
     * @see ClassLoader#getClassLoadingLock(String)
     * @since 1.553
     */
    private static Object getClassLoadingLock(ClassLoader cl, String name) {
        if (cl instanceof JenkinsClassLoader) {
            return ((JenkinsClassLoader) cl).getClassLoadingLock(name);
        }
        return invoke(GetClassLoadingLock.GET_CLASS_LOADING_LOCK, RuntimeException.class, cl, name);
    }

    private static class GetClassLoadingLock {
        private static final Method GET_CLASS_LOADING_LOCK;

        static {
            Method gCLL;
            try {
                gCLL = ClassLoader.class.getDeclaredMethod("getClassLoadingLock", String.class);
                gCLL.setAccessible(true);
            } catch (NoSuchMethodException x) {
                throw new AssertionError(x);
            }
            GET_CLASS_LOADING_LOCK = gCLL;
        }
    }

    private static class FindLoadedClass {
        private static final Method FIND_LOADED_CLASS;

        static {
            try {
                FIND_LOADED_CLASS = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
            } catch (NoSuchMethodException e) {
                throw new AssertionError(e);
            }
            FIND_LOADED_CLASS.setAccessible(true);
        }
    }

    private static class FindClass {
        private static final Method FIND_CLASS;

        static {
            try {
                FIND_CLASS = ClassLoader.class.getDeclaredMethod("findClass", String.class);
            } catch (NoSuchMethodException e) {
                throw new AssertionError(e);
            }
            FIND_CLASS.setAccessible(true);
        }
    }

    /**
     * Load the class with the specified binary name. This method searches for classes in the
     * following order:
     *
     * <ol>
     *   <li>
     *       <p>Invoke {@link ClassLoader#findLoadedClass(String)} to check if the class has already
     *       been loaded.
     *   <li>
     *       <p>Invoke {@link ClassLoader#findClass(String)} to find the class.
     * </ol>
     *
     * <p>This method synchronizes on the result of {@link ClassLoader#getClassLoadingLock(String)}
     * during the entire class loading process.
     *
     * @param cl The {@link ClassLoader} to use.
     * @param name The binary name of the class.
     * @return The resulting {@link Class} object.
     * @throws ClassNotFoundException If the class could not be found.
     * @see ClassLoader#loadClass(String)
     * @since 2.321
     */
    public static @NonNull Class<?> loadClass(ClassLoader cl, String name) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(cl, name)) {
            // First, check if the class has already been loaded.
            Class<?> c;
            if (cl instanceof JenkinsClassLoader) {
                c = ((JenkinsClassLoader) cl).findLoadedClass2(name);
            } else {
                c = (Class<?>) invoke(FindLoadedClass.FIND_LOADED_CLASS, RuntimeException.class, cl, name);
            }
            if (c == null) {
                // Find the class.
                if (cl instanceof JenkinsClassLoader) {
                    c = ((JenkinsClassLoader) cl).findClass(name);
                } else {
                    c = (Class<?>) invoke(FindClass.FIND_CLASS, ClassNotFoundException.class, cl, name);
                }
            }
            return c;
        }
    }

    /**
     * Find the resource with the given name.
     *
     * @param name The resource name.
     * @return {@link URL} object for reading the resource; {@code null} if the resource could not
     *     be found, a {@link URL} could not be constructed to locate the resource, the resource is
     *     in a package that is not opened unconditionally, or access to the resource is denied by
     *     the security manager.
     * @see ClassLoader#findResource(String)
     * @since 1.553
     */
    public static @CheckForNull URL _findResource(ClassLoader cl, String name) {
        URL url;
        if (cl instanceof JenkinsClassLoader) {
            url = ((JenkinsClassLoader) cl).findResource(name);
        } else if (cl instanceof URLClassLoader) {
            url = ((URLClassLoader) cl).findResource(name);
        } else {
            url = (URL) invoke(FindResource.FIND_RESOURCE, RuntimeException.class, cl, name);
        }

        return url;
    }

    private static class FindResource {
        private static final Method FIND_RESOURCE;

        static {
            try {
                FIND_RESOURCE = ClassLoader.class.getDeclaredMethod("findResource", String.class);
            } catch (NoSuchMethodException e) {
                throw new AssertionError(e);
            }
            FIND_RESOURCE.setAccessible(true);
        }
    }

    /**
     * Return an enumeration of {@link URL} objects representing all the resources with the given
     * name.
     *
     * @param name The resource name.
     * @return An enumeration of {@link URL} objects for the resource. If no resources could be
     *     found, the enumeration will be empty. Resources for which a {@link URL} cannot be
     *     constructed, which are in a package that is not opened unconditionally, or for which
     *     access to the resource is denied by the security manager, are not returned in the
     *     enumeration.
     * @throws IOException If I/O errors occur.
     * @see ClassLoader#findResources(String)
     * @since 1.553
     */
    @SuppressWarnings("unchecked")
    public static @NonNull Enumeration<URL> _findResources(ClassLoader cl, String name) throws IOException {
        Enumeration<URL> urls;
        if (cl instanceof JenkinsClassLoader) {
            urls = ((JenkinsClassLoader) cl).findResources(name);
        } else {
            urls = (Enumeration<URL>) invoke(FindResources.FIND_RESOURCES, IOException.class, cl, name);
        }

        return urls;
    }

    private static class FindResources {
        private static final Method FIND_RESOURCES;

        static {
            try {
                FIND_RESOURCES = ClassLoader.class.getDeclaredMethod("findResources", String.class);
            } catch (NoSuchMethodException e) {
                throw new AssertionError(e);
            }
            FIND_RESOURCES.setAccessible(true);
        }
    }
}
