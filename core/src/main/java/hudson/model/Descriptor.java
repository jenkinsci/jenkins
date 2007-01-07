package hudson.model;

import hudson.XmlFile;
import hudson.scm.CVSSCM;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Metadata about a configurable instance.
 *
 * <p>
 * {@link Descriptor} is an object that has metadata about a {@link Describable}
 * object, and also serves as a factory. A {@link Descriptor}/{@link Describable}
 * combination is used throughout in Hudson to implement a
 * configuration/extensibility mechanism.
 *
 * <p>
 * For example, Take the CVS support as an example, which is implemented
 * in {@link CVSSCM} class. Whenever a job is configured with CVS, a new
 * {@link CVSSCM} instance is created with the per-job configuration
 * information. This instance gets serialized to XML, and this instance
 * will be called to perform CVS operations for that job. This is the job
 * of {@link Describable} &mdash; each instance represents a specific
 * configuration of the CVS support (branch, CVSROOT, etc.)
 *
 * <p>
 * For Hudson to create such configured {@link CVSSCM} instance, Hudson
 * needs another object that captures the metadata of {@link CVSSCM},
 * and that is what a {@link Descriptor} is for. {@link CVSSCM} class
 * has a singleton descriptor, and this descriptor helps render
 * the configuration form, remember system-wide configuration (such as
 * where <tt>cvs.exe</tt> is), and works as a factory.
 *
 * <p>
 * {@link Descriptor} also usually have its associated views.
 *
 *
 * <h2>Persistence</h2>
 * <p>
 * {@link Descriptor} can persist data just by storing them in fields.
 * However, it is the responsibility of the derived type to properly
 * invoke {@link #save()} and {@link #load()}.
 *
 * @author Kohsuke Kawaguchi
 * @see Describable
 */
public abstract class Descriptor<T extends Describable<T>> {
    /**
     * Up to Hudson 1.61 this was used as the primary persistence mechanism.
     * Going forward Hudson simply persists all the non-transient fields
     * of {@link Descriptor}, just like others, so this is pointless.
     *
     * @deprecated
     */
    @Deprecated
    private transient Map<String,Object> properties;

    /**
     * The class being described by this descriptor.
     */
    public transient final Class<? extends T> clazz;

    protected Descriptor(Class<? extends T> clazz) {
        this.clazz = clazz;
        // doing this turns out to be very error prone,
        // as field initializers in derived types will override values.
        // load();
    }

    /**
     * Human readable name of this kind of configurable object.
     */
    public abstract String getDisplayName();

    /**
     * Creates a configured instance from the submitted form.
     *
     * <p>
     * Hudson only invokes this method when the user wants an instance of <tt>T</tt>.
     * So there's no need to check that in the implementation.
     *
     * @param req
     *      Always non-null. This object includes all the submitted form values.
     *
     * @throws FormException
     *      Signals a problem in the submitted form.
     */
    public abstract T newInstance(StaplerRequest req) throws FormException;

    /**
     * Returns the resource path to the help screen HTML, if any.
     */
    public String getHelpFile() {
        return "";
    }

    /**
     * Checks if the given object is created from this {@link Descriptor}.
     */
    public final boolean isInstance( T instance ) {
        return clazz.isInstance(instance);
    }

    /**
     * @deprecated
     *      As of 1.64. Use {@link #configure(StaplerRequest)}.
     */
    @Deprecated
    public boolean configure( HttpServletRequest req ) throws FormException {
        return true;
    }

    /**
     * Invoked when the global configuration page is submitted.
     *
     * Can be overriden to store descriptor-specific information.
     *
     * @return false
     *      to keep the client in the same config page.
     */
    public boolean configure( StaplerRequest req ) throws FormException {
        // compatibility
        return configure( (HttpServletRequest) req );
    }

    public final String getConfigPage() {
        return '/'+clazz.getName().replace('.','/').replace('$','/')+"/config.jelly";
    }

    public final String getGlobalConfigPage() {
        return '/'+clazz.getName().replace('.','/').replace('$','/')+"/global.jelly";
    }


    /**
     * Saves the configuration info to the disk.
     */
    protected synchronized void save() {
        try {
            getConfigFile().write(this);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save "+getConfigFile(),e);
        }
    }

    /**
     * Loads the data from the disk into this object.
     */
    protected synchronized void load() {
        XmlFile file = getConfigFile();
        if(!file.exists())
            return;

        try {
            Object o = file.unmarshal(this);
            if(o instanceof Map) {
                // legacy format
                @SuppressWarnings("unchecked")
                Map<String,Object> _o = (Map) o;
                convert(_o);
                save();     // convert to the new format
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load "+file, e);
        }
    }

    /**
     * {@link Descriptor}s that has existed &lt;= 1.61 needs to
     * be able to read in the old configuration in a property bag
     * and reflect that into the new layout.
     */
    protected void convert(Map<String, Object> oldPropertyBag) {
    }

    private XmlFile getConfigFile() {
        return new XmlFile(new File(Hudson.getInstance().getRootDir(),clazz.getName()+".xml"));
    }

    // to work around warning when creating a generic array type
    public static <T> T[] toArray( T... values ) {
        return values;
    }

    public static <T> List<T> toList( T... values ) {
        final ArrayList<T> r = new ArrayList<T>();
        for (T v : values)
            r.add(v);
        return r;
    }

    public static <T extends Describable<T>>
    Map<Descriptor<T>,T> toMap(Iterable<T> describables) {
        Map<Descriptor<T>,T> m = new LinkedHashMap<Descriptor<T>,T>();
        for (T d : describables) {
            m.put(d.getDescriptor(),d);
        }
        return m;
    }

    public static final class FormException extends Exception {
        private final String formField;

        public FormException(String message, String formField) {
            super(message);
            this.formField = formField;
        }

        public FormException(String message, Throwable cause, String formField) {
            super(message, cause);
            this.formField = formField;
        }

        public FormException(Throwable cause, String formField) {
            super(cause);
            this.formField = formField;
        }

        /**
         * Which form field contained an error?
         */
        public String getFormField() {
            return formField;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(Descriptor.class.getName());
}
