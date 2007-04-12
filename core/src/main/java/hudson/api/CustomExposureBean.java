package hudson.api;

/**
 * Interface that an exposed bean can implement, to do the equivalent
 * of <tt>writeReplace</tt> in Java serialization.
 * @author Kohsuke Kawaguchi
 */
public interface CustomExposureBean {
    /**
     * The returned object will be introspected and written as JSON/XML.
     */
    Object toExposedObject();
}
