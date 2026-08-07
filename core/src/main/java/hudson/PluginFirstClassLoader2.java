package hudson;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.util.CompoundEnumeration;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Objects;
import jenkins.util.URLClassLoader2;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Class loader that consults the plugin's {@code WEB-INF/lib/*.jar} and {@code WEB-INF/classes}
 * directories and the Jenkins core class loader (in that order).
 *
 * <p>To use this class loader, set {@code pluginFirstClassLoader} to {@code true} in the {@code
 * maven-hpi-plugin} configuration.
 *
 * @author Basil Crow
 */
@Restricted(NoExternalUse.class)
public class PluginFirstClassLoader2 extends URLClassLoader2 {
    static {
        registerAsParallelCapable();
    }


    public PluginFirstClassLoader2(String name, @NonNull URL[] urls, @NonNull ClassLoader parent) {
        super(name, Objects.requireNonNull(urls), Objects.requireNonNull(parent));
    }

    /**
     * Load the class with the specified binary name. This method searches for classes in the
     * following order:
     *
     * <ol>
     *   <li>
     *       <p>Invoke {@link #findLoadedClass(String)} to check if the class has already been
     *       loaded.
     *   <li>
     *       <p>Invoke {@link #findClass(String)} to find the class.
     *   <li>
     *       <p>Invoke {@link #loadClass(String)} on the parent class loader.
     * </ol>
     *
     * <p>If the class was found using the above steps and the {@code resolve} flag is true, this
     * method will then invoke {@link #resolveClass(Class)} on the resulting {@link Class} object.
     *
     * <p>This method synchronizes on the result of {@link #getClassLoadingLock(String)} during the
     * entire class loading process.
     *
     * @param name The binary name of the class
     * @param resolve If {@code true} then resolve the class
     * @return The resulting {@link Class} object
     * @throws ClassNotFoundException If the class could not be found
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                try {
                    c = findClass(name);
                } catch (ClassNotFoundException e) {
                    // ignore
                }
            }
            if (c == null) {
                c = getParent().loadClass(name);
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }

    /**
     * Find the resource with the given name. A resource is some data (images, audio, text, etc)
     * that can be accessed by class code in a way that is independent of the location of the code.
     *
     * <p>The name of a resource is a '{@code /}'-separated path name that identifies the resource.
     * This method searches for resources in the following order:
     *
     * <ol>
     *   <li>
     *       <p>Invoke {@link #findResource(String)} to find the resource.
     *   <li>
     *       <p>Invoke {@link #getResource(String)} on the parent class loader.
     * </ol>
     *
     * @param name The resource name
     * @return {@link URL} object for reading the resource; {@code null} if the resource could not
     *     be found, a {@link URL} could not be constructed to locate the resource, the resource is
     *     in a package that is not opened unconditionally, or access to the resource is denied by
     *     the security manager.
     * @throws NullPointerException If {@code name} is {@code null}
     */
    @Override
    public URL getResource(String name) {
        Objects.requireNonNull(name);
        URL url = findResource(name);
        if (url == null) {
            url = getParent().getResource(name);
        }
        return url;
    }

    /**
     * Find all the resources with the given name. A resource is some data (images, audio, text,
     * etc) that can be accessed by class code in a way that is independent of the location of the
     * code.
     *
     * <p>The name of a resource is a {@code /}-separated path name that identifies the resource.
     * This method first invokes {@link #findResources(String)} to find the resources with the name
     * in this class loader. Finally, it invokes {@link #getResources(String)} on the parent class
     * loader. It returns an enumeration whose elements are the {@link URL}s found by searching the
     * {@link URL}s found with {@link #findResources(String)}, followed by the {@link URL}s found by
     * searching the parent class loader.
     *
     * @param name The resource name
     * @return An enumeration of {@link URL} objects for the resource. If no resources could be
     *     found, the enumeration will be empty. Resources for which a {@link URL} cannot be
     *     constructed, which are in a package that is not opened unconditionally, or for which
     *     access to the resource is denied by the security manager, are not returned in the
     *     enumeration.
     * @throws IOException If I/O errors occur
     * @throws NullPointerException If {@code name} is {@code null}
     */
    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Objects.requireNonNull(name);
        return new CompoundEnumeration<>(findResources(name), getParent().getResources(name));
    }
}
