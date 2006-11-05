package hudson;

/**
 * Marker interface that designates extensible components
 * in Hudson that can be implemented by {@link Plugin}s.
 *
 * <p>
 * Interfaces/classes that implement this interface can be extended by plugins.
 * See respective interfaces/classes for more about how to register custom
 * implementations to Hudson.
 *
 * @author Kohsuke Kawaguchi
 * @see Plugin
 */
public interface ExtensionPoint {
}
