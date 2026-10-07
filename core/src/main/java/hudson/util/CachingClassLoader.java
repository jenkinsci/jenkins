package hudson.util;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 *
 * ClassLoader with internal caching of class loading results.
 *
 * <p>
 * Caches both successful and failed class lookups to avoid redundant delegation
 * and repeated class resolution attempts. Designed for performance optimization
 * in systems that repeatedly query class presence (e.g., plugin environments,
 * reflective loading, optional dependencies).
 * </p>
 *
 * Useful for classloaders that have heavy-weight loadClass() implementations
 *
 * @author Dmytro Ukhlov
 */
@Restricted(NoExternalUse.class)
public class CachingClassLoader extends DelegatingClassLoader {
    private final ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();

    public CachingClassLoader(String name, ClassLoader parent) {
        super(name, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Object classOrEmpty = cache.computeIfAbsent(name, key -> {
            try {
                return super.loadClass(name, false);
            } catch (ClassNotFoundException e) {
                // Not found.
                return Optional.empty();
            }
        });

        if (classOrEmpty == Optional.empty()) {
            throw new ClassNotFoundException(name);
        }

        Class<?> clazz = (Class<?>) classOrEmpty;
        if (resolve) {
            resolveClass(clazz);
        }
        return clazz;
    }

    public void clearCacheMisses() {
        cache.values().removeIf(v -> v == Optional.empty());
    }
}
