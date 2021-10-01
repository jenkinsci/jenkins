package jenkins;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import jenkins.util.JenkinsClassLoader;

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
@SuppressWarnings({"unchecked", "rawtypes"})
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

    /**
     * Calls {@link ClassLoader#findLoadedClass} while holding {@link ClassLoader#getClassLoadingLock}.
     * @since 1.553
     */
    public static @CheckForNull Class<?> _findLoadedClass(ClassLoader cl, String name) {
        synchronized (getClassLoadingLock(cl, name)) {
            Class<?> c;
            if (cl instanceof JenkinsClassLoader) {
                c = ((JenkinsClassLoader) cl).findLoadedClass2(name);
            } else {
                c = (Class) invoke(FindLoadedClass.FIND_LOADED_CLASS, RuntimeException.class, cl, name);
            }

            return c;
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

    /**
     * Calls {@link ClassLoader#findClass} while holding {@link ClassLoader#getClassLoadingLock}.
     * @since 1.553
     */
    public static @NonNull Class<?> _findClass(ClassLoader cl, String name) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(cl, name)) {
            if (cl instanceof JenkinsClassLoader) {
                return ((JenkinsClassLoader) cl).findClass(name);
            } else {
                return (Class) invoke(FindClass.FIND_CLASS, ClassNotFoundException.class, cl, name);
            }
        }
    }

    private static class FindClass {
        private static final Method FIND_CLASS;

        static {
            try {
                FIND_CLASS = ClassLoader.class.getDeclaredMethod("findClass",String.class);
            } catch (NoSuchMethodException e) {
                throw new AssertionError(e);
            }
            FIND_CLASS.setAccessible(true);
        }
    }


    /**
     * Calls {@link ClassLoader#findResource}.
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
     * Calls {@link ClassLoader#findResources}.
     * @since 1.553
     */
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

    /** @deprecated unsafe */
    @Deprecated public ClassLoaderReflectionToolkit() {}

    /** @deprecated unsafe */
    @Deprecated
    public Class findLoadedClass(ClassLoader cl, String name) throws InvocationTargetException {
        try {
            return (Class)FindLoadedClass.FIND_LOADED_CLASS.invoke(cl,name);
        } catch (IllegalAccessException e) {
            throw new LinkageError(e.getMessage(), e);
        }
    }

    /** @deprecated unsafe */
    @Deprecated
    public Class findClass(ClassLoader cl, String name) throws InvocationTargetException {
        try {
            return (Class)FindClass.FIND_CLASS.invoke(cl,name);
        } catch (IllegalAccessException e) {
            throw new LinkageError(e.getMessage(), e);
        }
    }

    /** @deprecated unsafe */
    @Deprecated
    public URL findResource(ClassLoader cl, String name) throws InvocationTargetException {
        try {
            return (URL)FindResource.FIND_RESOURCE.invoke(cl,name);
        } catch (IllegalAccessException e) {
            throw new LinkageError(e.getMessage(), e);
        }
    }

    /** @deprecated unsafe */
    @Deprecated
    public Enumeration<URL> findResources(ClassLoader cl, String name) throws InvocationTargetException {
        try {
            return (Enumeration<URL>)FindResources.FIND_RESOURCES.invoke(cl,name);
        } catch (IllegalAccessException e) {
            throw new LinkageError(e.getMessage(), e);
        }
    }

}
