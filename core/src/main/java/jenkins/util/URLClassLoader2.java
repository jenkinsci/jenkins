package jenkins.util;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import jenkins.ClassLoaderReflectionToolkit;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * {@link URLClassLoader} with loosened visibility for use with {@link
 * ClassLoaderReflectionToolkit}.
 */
@Restricted(NoExternalUse.class)
public class URLClassLoader2 extends URLClassLoader implements JenkinsClassLoader {
    private static final AtomicInteger NEXT_INSTANCE_NUMBER = new AtomicInteger(0);

    private final String lockObjectPrefixName = String.format("%s@%x-loadClassLock:",
            URLClassLoader2.class.getName(), NEXT_INSTANCE_NUMBER.getAndIncrement());

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

    /**
     * Replace the JDK's per-name lock map with a GC-collectable lock object.
     * See also: <a href="https://mail.openjdk.org/pipermail/core-libs-dev/2025-May/146392.html">OpenJDK MailThread</a>
     *
     * <p>Parallel-capable {@link ClassLoader} implementations keep a distinct lock object per class
     * name indefinitely, which can retain huge maps when there are many misses. Returning an
     * interned {@link String} keyed by this loader and the class name preserves mutual exclusion
     * for a given (loader, name) pair but allows the JVM to reclaim the lock when no longer
     * referenced. Interned Strings are heap objects and GC-eligible on modern JDKs (7+).
     *
     * @param className the binary name of the class being loaded (must not be null)
     * @return a lock object unique to this classloader/class pair
     */
    @Override
    public Object getClassLoadingLock(String className) {
        Objects.requireNonNull(className);
        return (lockObjectPrefixName + className).intern();
    }
}
