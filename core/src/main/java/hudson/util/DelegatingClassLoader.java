package hudson.util;

import java.util.Objects;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * A {@link ClassLoader} that does not define any classes itself but delegates class loading to other class loaders to
 * avoid the JDK's per-class-name locking and lock retention.
 * <p>
 * This class first attempts to load classes via its {@link ClassLoader#getParent} class loader, then falls back to
 * {@link ClassLoader#findClass} to allow for custom delegation logic.
 * <p>
 * In a parallel-capable {@link ClassLoader{, the JDK maintains a per-name lock object indefinitely. In Jenkins, many
 * class loading misses across many loaders can accumulate hundreds of thousands of such locks, retaining significant
 * memory. This loader never defines classes and bypasses {@link ClassLoader}'s default {@code loadClass} locking; it
 * delegates to the parent first and then to {@code findClass} for custom delegation.
 * <p>
 * The actual defining loader (parent or a delegate) still performs the necessary synchronization and class definition.
 * A runtime guard ({@link #verify}) throws if this loader ever becomes the defining loader.
 * <p>
 * Subclasses must not call {@code defineClass}; implement delegation in {@code findClass} if needed and do not mark
 * subclasses as parallel-capable.
 *
 * @author Dmytro Ukhlov
 */
@Restricted(NoExternalUse.class)
public class DelegatingClassLoader extends ClassLoader {
    protected DelegatingClassLoader(String name, ClassLoader parent) {
        super(name, Objects.requireNonNull(parent));
    }

    public DelegatingClassLoader(ClassLoader parent) {
        super(Objects.requireNonNull(parent));
    }

    /**
     * Parent-first delegation without synchronizing on {@link #getClassLoadingLock(String)}. This
     * prevents creation/retention of per-name lock objects in a loader that does not define
     * classes. The defining loader downstream still serializes class definition as required.
     *
     * @param name The binary name of the class
     * @param resolve If {@code true} then resolve the class
     * @return The resulting {@link Class} object
     * @throws ClassNotFoundException If the class could not be found
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> c = null;
        try {
            c = getParent().loadClass(name);
        } catch (ClassNotFoundException e) {
            // ClassNotFoundException thrown if class not found
            // from the non-null parent class loader
        }

        if (c == null) {
            // If still not found, then invoke findClass in order
            // to find the class.
            c = findClass(name);
        }

        c = verify(c);
        if (resolve) {
            resolveClass(c);
        }
        return c;
    }

    /**
     * Safety check to ensure this delegating loader never becomes the defining loader.
     *
     * <p>Fails fast if a subclass erroneously defines a class here, which would violate the
     * delegation-only contract and could reintroduce locking/retention issues.
     */
    protected Class<?> verify(Class<?> clazz) {
        if (clazz.getClassLoader() == this) {
            throw new IllegalStateException("DelegatingClassLoader must not be the defining loader: " + clazz.getName());
        }
        return clazz;
    }
}
