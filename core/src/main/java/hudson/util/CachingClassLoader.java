package hudson.util;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
public class CachingClassLoader extends DelegatingClassLoader {
    private final ConcurrentHashMap<String, Object> loaded = new ConcurrentHashMap<>();

    public CachingClassLoader(String name, ClassLoader parent) {
        super(name, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Object classOrEmpty = loaded.computeIfAbsent(name, key -> {
            try {
                return doLoadClass(name);
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

    /**
     * Do effective class loading
     *
     * @param name class name to load
     *
     * @return loaded class
     *
     * @throws ClassNotFoundException if class not found
     */
    protected Class<?> doLoadClass(String name) throws ClassNotFoundException {
        return super.loadClass(name, false);
    }

    public void clearCacheMisses() {
        loaded.values().removeIf(v -> v == Optional.empty());
    }
}
