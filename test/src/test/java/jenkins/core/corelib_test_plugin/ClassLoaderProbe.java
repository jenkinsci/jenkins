package jenkins.core.corelib_test_plugin;

/**
 * @see jenkins.core.CoreLibIsolationRealTest
 */
public class ClassLoaderProbe {

    /**
     * Attempts to load a class from the current classloader context.
     *
     * @param className the fully qualified class name to load
     * @return true if the class can be loaded, false otherwise
     */
    public static boolean canLoadClass(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Gets the classloader that loaded this class.
     */
    public static ClassLoader getClassLoader() {
        return ClassLoaderProbe.class.getClassLoader();
    }
}
