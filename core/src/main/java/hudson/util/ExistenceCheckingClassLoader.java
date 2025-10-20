package hudson.util;

import java.util.Objects;
import jenkins.util.URLClassLoader2;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * A {@link ClassLoader} that verifies the existence of a {@code .class} resource before attempting
 * to load the class. Intended to sit in front of servlet container loaders we do not control.
 *
 * <p>This implementation overrides {@link #loadClass(String, boolean)} and uses {@link
 * #getResource(String)} to check whether the corresponding <code>.class</code> file is available in
 * the classpath. If the resource is not found, a {@link ClassNotFoundException} is thrown
 * immediately.
 *
 * <p>Parallel-capable parent loaders retain a per-class-name lock object for every load attempt,
 * including misses. By checking getResource(name + ".class") first and throwing {@link
 * ClassNotFoundException} on absence, we avoid calling {@code loadClass} on misses, thus preventing
 * the parent from populating its lock map for nonexistent classes.
 *
 * <p>This class is only needed in {@link hudson.PluginManager.UberClassLoader}. It is unnecessary
 * for plugin {@link ClassLoader}s (because {@link URLClassLoader2} mitigates lock retention via
 * {@link ClassLoader#getClassLoadingLock}) and redundant for delegators (because {@link
 * DelegatingClassLoader} already avoids base locking).
 *
 * @author Dmytro Ukhlov
 * @see ClassLoader
 * @see #getResource(String)
 */
@Restricted(NoExternalUse.class)
public final class ExistenceCheckingClassLoader extends DelegatingClassLoader {

    public ExistenceCheckingClassLoader(String name, ClassLoader parent) {
        super(name, Objects.requireNonNull(parent));
    }

    public ExistenceCheckingClassLoader(ClassLoader parent) {
        super(Objects.requireNonNull(parent));
    }

    /**
     * Short-circuits misses by checking for the {@code .class} resource prior to delegation.
     * Successful loads behave exactly as the parent would; misses do not touch the parent's
     * per-name lock map.
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Add support for loading of JaCoCo dynamic instrumentation classes
        if (name.equals("java.lang.$JaCoCo")) {
            return super.loadClass(name, resolve);
        }

        if (getResource(name.replace('.', '/') + ".class") == null) {
            throw new ClassNotFoundException(name);
        }

        return super.loadClass(name, resolve);
    }
}
