package hudson.util;

/**
 * A class loader that verifies the existence of a class resource before attempting to load the class.
 * <p>
 * This implementation overrides {@link #loadClass(String, boolean)} and uses {@link #getResource(String)}
 * to check whether the corresponding <code>.class</code> file is available in the classpath.
 * If the resource is not found, a {@link ClassNotFoundException} is thrown immediately.
 * </p>
 *
 * <p>This approach can be useful in environments where conditional class availability
 * must be verified without triggering side effects from the loading process.</p>
 *
 * @see ClassLoader
 * @see #getResource(String)
 */
public class ExistenceCheckingClassLoader extends DelegatingClassLoader {

    public ExistenceCheckingClassLoader(String name, ClassLoader parent) {
        super(name, parent);
    }

    public ExistenceCheckingClassLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (getResource(name.replace('.', '/') + ".class") == null &&
                !name.equals("java.lang.$JaCoCo")) { // Add support for loading of JaCoCo dynamic instrumentation classes
            throw new ClassNotFoundException(name);
        }

        return super.loadClass(name, resolve);
    }
}
