package jenkins;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.util.AntClassLoader;
import jenkins.util.AntWithFindResourceClassLoader;

/**
 * Reflective access to various {@link ClassLoader} methods which are otherwise {@code protected}.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class ClassLoaderReflectionToolkit {

    private static Method FIND_CLASS, FIND_LOADED_CLASS, FIND_RESOURCE, FIND_RESOURCES, GET_CLASS_LOADING_LOCK;

    private static <T extends Exception> Object invoke(Method method, Class<T> exception, Object obj, Object... args) throws T {
        try {
            return method.invoke(obj, args);
        } catch (IllegalAccessException x) {
            throw new AssertionError(x);
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
        if (cl instanceof AntWithFindResourceClassLoader) {
            return ((AntWithFindResourceClassLoader) cl).getClassLoadingLock(name);
        }
        initClassLoadingLock();

        return invoke(GET_CLASS_LOADING_LOCK, RuntimeException.class, cl, name);
    }

    private static void initClassLoadingLock() {
        if (GET_CLASS_LOADING_LOCK == null) {
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
            if (cl instanceof AntWithFindResourceClassLoader) {
                c = ((AntWithFindResourceClassLoader) cl).findLoadedClass2(name);
            } else {
                initFindLoadedClass();
                c = ClassLoaderReflectionToolkit._findLoadedClass(cl, name);
            }

            return c;
        }
    }

    private static void initFindLoadedClass() {
        if (FIND_LOADED_CLASS == null) {
            try {
                FIND_LOADED_CLASS = ClassLoader.class.getDeclaredMethod("findLoadedClass",String.class);
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
        if (cl instanceof AntClassLoader) {
            return ((AntClassLoader) cl).findClass(name);
        }

        synchronized (getClassLoadingLock(cl, name)) {
            initFindClass();
            return (Class) invoke(FIND_CLASS, ClassNotFoundException.class, cl, name);
        }
    }

    private static void initFindClass() {
        if (FIND_CLASS == null) {
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
        if (cl instanceof AntWithFindResourceClassLoader) {
            url = ((AntWithFindResourceClassLoader) cl).findResource(name);
        } else if (cl instanceof URLClassLoader) {
            url = ((URLClassLoader) cl).findResource(name);
        } else {
            initFindResource();
            url = (URL) invoke(FIND_RESOURCE, RuntimeException.class, cl, name);
        }

        return url;
    }

    private static void initFindResource() {
        if (FIND_RESOURCE == null) {
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
        if (cl instanceof AntWithFindResourceClassLoader) {
            urls = ((AntWithFindResourceClassLoader) cl).findResources(name);
        } else {
            initFindResources();
            urls = (Enumeration<URL>) invoke(FIND_RESOURCES, IOException.class, cl, name);
        }

        return urls;
    }

    private static void initFindResources() {
        if (FIND_RESOURCES == null) {
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
            initFindLoadedClass();
            return (Class)FIND_LOADED_CLASS.invoke(cl,name);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        }
    }

    /** @deprecated unsafe */
    @Deprecated
    public Class findClass(ClassLoader cl, String name) throws InvocationTargetException {
        try {
            initFindClass();
            return (Class)FIND_CLASS.invoke(cl,name);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        }
    }

    /** @deprecated unsafe */
    @Deprecated
    public URL findResource(ClassLoader cl, String name) throws InvocationTargetException {
        try {
            initFindResource();
            return (URL)FIND_RESOURCE.invoke(cl,name);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        }
    }

    /** @deprecated unsafe */
    @Deprecated
    public Enumeration<URL> findResources(ClassLoader cl, String name) throws InvocationTargetException {
        try {
            initFindResources();
            return (Enumeration<URL>)FIND_RESOURCES.invoke(cl,name);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        }
    }

}
