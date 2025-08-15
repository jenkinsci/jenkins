package hudson.util;

import java.util.Objects;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * A {@link ClassLoader} that does not define any classes itself but delegates class loading
 * to other class loaders. It first attempts to load classes via its {@code getParent()} class loader,
 * then falls back to {@code findClass} to allow for custom delegation logic.
 * <p>
 * This class can also serve as the parent class loader for other class loaders that follow
 * the standard delegation model.
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
     * Overrides base implementation to skip unnecessary synchronization
     *
     * @param   name
     *          The <a href="#binary-name">binary name</a> of the class
     *
     * @param   resolve
     *          If {@code true} then resolve the class
     *
     * @return  The resulting {@code Class} object
     *
     * @throws  ClassNotFoundException
     *          If the class could not be found
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

    protected Class<?> verify(Class<?> clazz) {
        if (clazz.getClassLoader() == this) {
            throw new IllegalStateException("DelegatingClassLoader must not be the defining loader: " + clazz.getName());
        }
        return clazz;
    }
}
