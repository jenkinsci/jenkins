package jenkins.util;

import hudson.PluginManager;
import hudson.remoting.ObjectInputStreamEx;
import java.io.ObjectInputStream;

/**
 * Java defines a {@link Thread#getContextClassLoader}. Jenkins does not use this much; it will
 * normally be set by the servlet container to the Jenkins core class loader.
 *
 * <p>Some Java libraries have a fundamental design flaw, originating in premodular systems with a
 * "flat classpath", whereby they expect {@link Thread#getContextClassLoader} to have access to the
 * same classes as the class loader of the calling class. This fails in Jenkins, because {@link
 * Thread#getContextClassLoader} can only see Jenkins core, not plugins.
 *
 * <p>It is a design flaw in the library if it fails to allow clients to directly specify a {@link
 * ClassLoader} to use for lookups (or preregister {@link Class} instances for particular names).
 * Consider patching the library or looking harder for appropriate APIs that already exist. As an
 * example, {@link ObjectInputStream} (used for deserializing Java objects) by default uses a
 * complicated algorithm to guess at a {@link ClassLoader}, but you can override {@link
 * ObjectInputStream#resolveClass} to remove the need for guessing (as {@link ObjectInputStreamEx}
 * in fact does).
 *
 * <p>Alternatively, work around the problem by applying {@link SetContextClassLoader} liberally in
 * a {@code try}-with-resources block wherever we might be calling into such a library:
 *
 * <pre>
 * class Caller {
 *     void foo() {
 *         try (SetContextClassLoader sccl = new SetContextClassLoader()) {
 *             [...] // Callee uses Thread.currentThread().getContextClassLoader()
 *         }
 *     }
 * }
 * </pre>
 *
 * <p>When called from a plugin, {@link #SetContextClassLoader()} should typically be used. This
 * implicitly uses the class loader of the calling class, which has access to all the plugin's
 * direct and transitive dependencies. Alternatively, the class loader of a specific class can be
 * used via {@link #SetContextClassLoader(Class)}. When the particular class loader needed is
 * unclear, {@link #SetContextClassLoader(ClassLoader)} can be used as a fallback with {@link
 * PluginManager.UberClassLoader} as the argument, though this is not as safe since lookups could be
 * ambiguous in case two unrelated plugins both bundle the same library. In functional tests, {@code
 * RealJenkinsRule.Endpoint} can be used to reference a class loader that has access to the plugins
 * defined in the test scenario.
 *
 * <p>See <a
 * href="https://www.jenkins.io/doc/developer/plugin-development/dependencies-and-class-loading/#context-class-loaders">the
 * developer documentation</a> for more information.
 *
 * @since 2.362
 */
public final class SetContextClassLoader implements AutoCloseable {

    private final Thread t;
    private final ClassLoader orig;

    /**
     * Change the {@link Thread#getContextClassLoader} associated with the current thread to that of
     * the calling class.
     *
     * @since 2.362
     */
    public SetContextClassLoader() {
        this(StackWalker.getInstance().getCallerClass());
    }

    /**
     * Change the {@link Thread#getContextClassLoader} associated with the current thread to that of
     * the specified class.
     *
     * @param clazz The {@link Class} whose {@link ClassLoader} to use.
     * @since 2.362
     */
    public SetContextClassLoader(Class<?> clazz) {
        this(clazz.getClassLoader());
    }

    /**
     * Change the {@link Thread#getContextClassLoader} associated with the current thread to the
     * specified {@link ClassLoader}.
     *
     * @param cl The {@link ClassLoader} to use.
     * @since 2.362
     */
    public SetContextClassLoader(ClassLoader cl) {
        t = Thread.currentThread();
        orig = t.getContextClassLoader();
        t.setContextClassLoader(cl);
    }

    @Override
    public void close() {
        t.setContextClassLoader(orig);
    }
}
