package jenkins;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Enumeration;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Reflective access to various {@link ClassLoader} methods which are otherwise {@code protected}.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class ClassLoaderReflectionToolkit {

    private static final Method FIND_CLASS, FIND_LOADED_CLASS, FIND_RESOURCE, FIND_RESOURCES, GET_CLASS_LOADING_LOCK;

    static {
        try {
            FIND_CLASS = ClassLoader.class.getDeclaredMethod("findClass",String.class);
            FIND_CLASS.setAccessible(true);
            FIND_LOADED_CLASS = ClassLoader.class.getDeclaredMethod("findLoadedClass",String.class);
            FIND_LOADED_CLASS.setAccessible(true);
            FIND_RESOURCE = ClassLoader.class.getDeclaredMethod("findResource",String.class);
            FIND_RESOURCE.setAccessible(true);
            FIND_RESOURCES = ClassLoader.class.getDeclaredMethod("findResources",String.class);
            FIND_RESOURCES.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
        Method gCLL = null;
        try {
            gCLL = ClassLoader.class.getDeclaredMethod("getClassLoadingLock", String.class);
            gCLL.setAccessible(true);
        } catch (NoSuchMethodException x) {
            throw new AssertionError(x);
        }
        GET_CLASS_LOADING_LOCK = gCLL;
    }

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
        return invoke(GET_CLASS_LOADING_LOCK, RuntimeException.class, cl, name);
    }

    /**
     * Calls {@link ClassLoader#findLoadedClass} while holding {@link ClassLoader#getClassLoadingLock}.
     * @since 1.553
     */
    public static @CheckForNull Class<?> _findLoadedClass(ClassLoader cl, String name) {
        synchronized (getClassLoadingLock(cl, name)) {
            return (Class) invoke(FIND_LOADED_CLASS, RuntimeException.class, cl, name);
        }
    }

    /**
     * Calls {@link ClassLoader#findClass} while holding {@link ClassLoader#getClassLoadingLock}.
     * @since 1.553
     */
    public static @Nonnull Class<?> _findClass(ClassLoader cl, String name) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(cl, name)) {
            return (Class) invoke(FIND_CLASS, ClassNotFoundException.class, cl, name);
        }
    }

    /**
     * Calls {@link ClassLoader#findResource}.
     * @since 1.553
     */
    public static @CheckForNull URL _findResource(ClassLoader cl, String name) {
        return (URL) invoke(FIND_RESOURCE, RuntimeException.class, cl, name);
    }

    /**
     * Calls {@link ClassLoader#findResources}.
     * @since 1.553
     */
    public static @Nonnull Enumeration<URL> _findResources(ClassLoader cl, String name) throws IOException {
        return (Enumeration<URL>) invoke(FIND_RESOURCES, IOException.class, cl, name);
    }

    /** @deprecated unsafe */
    @Deprecated public ClassLoaderReflectionToolkit() {}

    /** @deprecated unsafe */
    @Deprecated
    public Class findLoadedClass(ClassLoader cl, String name) throws InvocationTargetException {
        try {
            return (Class)FIND_LOADED_CLASS.invoke(cl,name);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        }
    }

    /** @deprecated unsafe */
    @Deprecated
    public Class findClass(ClassLoader cl, String name) throws InvocationTargetException {
        try {
            return (Class)FIND_CLASS.invoke(cl,name);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        }
    }

    /** @deprecated unsafe */
    @Deprecated
    public URL findResource(ClassLoader cl, String name) throws InvocationTargetException {
        try {
            return (URL)FIND_RESOURCE.invoke(cl,name);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        }
    }

    /** @deprecated unsafe */
    @Deprecated
    public Enumeration<URL> findResources(ClassLoader cl, String name) throws InvocationTargetException {
        try {
            return (Enumeration<URL>)FIND_RESOURCES.invoke(cl,name);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        }
    }

}
