package jenkins;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Enumeration;

/**
 * Reflection access to various {@link ClassLoader} methods.
 *
 * @author Kohsuke Kawaguchi
 */
public class ClassLoaderReflectionToolkit {
    /**
     * ClassLoader.findClass(String) for a call that bypasses access modifier.
     */
    private final Method FIND_CLASS, FIND_LOADED_CLASS, FIND_RESOURCE, FIND_RESOURCES;

    public ClassLoaderReflectionToolkit() {
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
    }

    public Class findLoadedClass(ClassLoader cl, String name) throws InvocationTargetException {
        try {
            return (Class)FIND_LOADED_CLASS.invoke(cl,name);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        }
    }

    public Class findClass(ClassLoader cl, String name) throws InvocationTargetException {
        try {
            return (Class)FIND_CLASS.invoke(cl,name);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        }
    }

    public URL findResource(ClassLoader cl, String name) throws InvocationTargetException {
        try {
            return (URL)FIND_RESOURCE.invoke(cl,name);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        }
    }

    public Enumeration<URL> findResources(ClassLoader cl, String name) throws InvocationTargetException {
        try {
            return (Enumeration<URL>)FIND_RESOURCES.invoke(cl,name);
        } catch (IllegalAccessException e) {
            throw new Error(e);
        }
    }

//    private void check(InvocationTargetException e) {
//        Throwable t = e.getTargetException();
//        if (t instanceof Error)
//            throw (Error)t;
//        if (t instanceof RuntimeException)
//            throw (RuntimeException)t;
//    }
}
