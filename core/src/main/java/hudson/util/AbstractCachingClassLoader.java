package hudson.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link ClassLoader} that caches loadClass invocation results
 *
 * <p>
 * This class is used for constructing a facade class loader which use a lot of other class sources.
 * It helps to avoid looking up using all the class sources each time
 *
 * @author Dmytro Ukhlov
 */
 public abstract class AbstractCachingClassLoader extends ClassLoader {
    private final ConcurrentHashMap<String, Class<?>> loadedClassMapping =  new ConcurrentHashMap<>();

    private final int missedClassCacheSize;

    private final Map<String, String> missedClassMapping = Collections.synchronizedMap(new LinkedHashMap<>() {
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > missedClassCacheSize;
        }
    });

    /**
     * Initialize base implementation, bypass parameters to java.lang.ClassLoader base implementation
     *
     * @param  name   class loader name; or {@code null} if not named
     * @param  parent the parent class loader
     *
     * @param  missedClassCacheSize maximum number of missed classes to be remembered
     *
     */
    protected AbstractCachingClassLoader(String name, ClassLoader parent, int missedClassCacheSize) {
        super(name, parent);
        this.missedClassCacheSize = missedClassCacheSize;
    }

    public final void clearCacheMisses() {
        missedClassMapping.clear();
    }

    @Override
    protected final Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (isClassKnownAsMissed(name)) {
            throw new ClassNotFoundException(name);
        }

        Class<?> clazz = loadedClassMapping.computeIfAbsent(name, key -> {
            if (getResource(name.replace('.', '/') + ".class") == null) {
                return null;
            }
            return doLoadClass(name);
        });

        if (clazz == null) {
            missedClassMapping.put(name, name);
            throw new ClassNotFoundException(name);
        }

        if (resolve) {
            resolveClass(clazz);
        }

        return clazz;
    }

    protected boolean isClassKnownAsMissed(String name) {
        return missedClassMapping.containsKey(name);
    }

    protected abstract Class<?> doLoadClass(String name);
}
