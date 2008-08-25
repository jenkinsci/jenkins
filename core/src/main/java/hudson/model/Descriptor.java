package hudson.model;

import hudson.XmlFile;
import hudson.BulkChange;
import hudson.util.CopyOnWriteList;
import hudson.scm.CVSSCM;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.Stapler;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Metadata about a configurable instance.
 *
 * <p>
 * {@link Descriptor} is an object that has metadata about a {@link Describable}
 * object, and also serves as a factory (in a way this relationship is similar
 * to {@link Object}/{@link Class} relationship.
 *
 * A {@link Descriptor}/{@link Describable}
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
public abstract class Descriptor<T extends Describable<T>> implements Saveable {
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

    private transient final Map<String,Method> checkMethods = new ConcurrentHashMap<String,Method>();

    protected Descriptor(Class<? extends T> clazz) {
        this.clazz = clazz;
        ALL.add(this);
        // doing this turns out to be very error prone,
        // as field initializers in derived types will override values.
        // load();
    }

    /**
     * Human readable name of this kind of configurable object.
     */
    public abstract String getDisplayName();

    /**
     * If the field "xyz" of a {@link Describable} has the corresponding "doCheckXyz" method,
     * return the form-field validation string. Otherwise null.
     * <p>
     * This method is used to hook up the form validation method to
     */
    public String getCheckUrl(String fieldName) {
        String capitalizedFieldName = StringUtils.capitalize(fieldName);

        Method method = checkMethods.get(fieldName);
        if(method==null) {
            method = NONE;
            String methodName = "doCheck"+ capitalizedFieldName;
            for( Method m : getClass().getMethods() ) {
                if(m.getName().equals(methodName)) {
                    method = m;
                    break;
                }
            }
            checkMethods.put(fieldName,method);
        }

        if(method==NONE)
            return null;

        return '\''+Stapler.getCurrentRequest().getContextPath()+"/descriptor/"+clazz.getName()+"/check"+capitalizedFieldName+"?value='+encode(this.value)";
    }

    /**
     * Gets the class name nicely escaped to be usable as a key in the structured form submission.
     */
    public String getJsonSafeClassName() {
        return clazz.getName().replace('.','-');
    }

    /**
     * @deprecated
     *      Implement {@link #newInstance(StaplerRequest, JSONObject)} method instead.
     *      Deprecated as of 1.145. 
     */
    public T newInstance(StaplerRequest req) throws FormException {
        throw new UnsupportedOperationException(getClass()+" should implement newInstance(StaplerRequest,JSONObject)");
    }

    /**
     * Creates a configured instance from the submitted form.
     *
     * <p>
     * Hudson only invokes this method when the user wants an instance of <tt>T</tt>.
     * So there's no need to check that in the implementation.
     *
     * <p>
     * Starting 1.206, the default implementation of this method does the following:
     * <pre>
     * req.bindJSON(clazz,formData);
     * </pre>
     * <p>
     * ... which performs the databinding on the constructor of {@link #clazz}.
     *
     * @param req
     *      Always non-null. This object includes represents the entire submisison.
     * @param formData
     *      The JSON object that captures the configuration data for this {@link Descriptor}.
     *      See http://hudson.gotdns.com/wiki/display/HUDSON/Structured+Form+Submission
     *
     * @throws FormException
     *      Signals a problem in the submitted form.
     * @since 1.145
     */
    public T newInstance(StaplerRequest req, JSONObject formData) throws FormException {
        try {
            Method m = getClass().getMethod("newInstance", StaplerRequest.class);

            if(!Modifier.isAbstract(m.getDeclaringClass().getModifiers())) {
                // this class overrides newInstance(StaplerRequest).
                // maintain the backward compatible behavior
                return newInstance(req);
            } else {
                // new behavior as of 1.206
                return req.bindJSON(clazz,formData);
            }
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e); // impossible
        }
    }

    /**
     * Returns the resource path to the help screen HTML, if any.
     *
     * <p>
     * This value is relative to the context root of Hudson, so normally
     * the values are something like <tt>"/plugin/emma/help.html"</tt> to
     * refer to static resource files in a plugin, or <tt>"/publisher/EmmaPublisher/abc"</tt>
     * to refer to Jelly script <tt>abc.jelly</tt> or a method <tt>EmmaPublisher.doAbc()</tt>.
     *
     * @return
     *      null to indicate that there's no help.
     */
    public String getHelpFile() {
        return null;
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
     * @deprecated
     *      As of 1.239, use {@link #configure(StaplerRequest, JSONObject)}.
     */
    public boolean configure( StaplerRequest req ) throws FormException {
        // compatibility
        return configure( (HttpServletRequest) req );
    }

    /**
     * Invoked when the global configuration page is submitted.
     *
     * Can be overriden to store descriptor-specific information.
     *
     * @param json
     *      The JSON object that captures the configuration data for this {@link Descriptor}.
     *      See http://hudson.gotdns.com/wiki/display/HUDSON/Structured+Form+Submission
     * @return false
     *      to keep the client in the same config page.
     */
    public boolean configure( StaplerRequest req, JSONObject json ) throws FormException {
        // compatibility
        return configure(req);
    }

    public String getConfigPage() {
        return getViewPage(clazz, "config.jelly");
    }

    public String getGlobalConfigPage() {
        return getViewPage(clazz, "global.jelly");
    }

    protected final String getViewPage(Class<?> clazz, String pageName) {
        while(clazz!=Object.class) {
            String name = clazz.getName().replace('.', '/').replace('$', '/') + "/" + pageName;
            if(clazz.getClassLoader().getResource(name)!=null)
                return '/'+name;
            clazz = clazz.getSuperclass();
        }
        return "none";
    }


    /**
     * Saves the configuration info to the disk.
     */
    public synchronized void save() {
        if(BulkChange.contains(this))   return;
        try {
            getConfigFile().write(this);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save "+getConfigFile(),e);
        }
    }

    /**
     * Loads the data from the disk into this object.
     *
     * <p>
     * The constructor of the derived class must call this method.
     * (If we do that in the base class, the derived class won't
     * get a chance to set default values.)
     */
    public synchronized void load() {
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

    /**
     * Used to build {@link Describable} instance list from &lt;f:hetero-list> tag.
     *
     * @param req
     *      Request that represents the form submission.
     * @param formData
     *      Structured form data that represents the contains data for the list of describables.
     * @param key
     *      The JSON property name for 'formData' that represents the data for the list of describables.
     * @param descriptors
     *      List of descriptors to create instances from.
     * @return
     *      Can be empty but never null.
     */
    public static <T extends Describable<T>>
    List<T> newInstancesFromHeteroList(StaplerRequest req, JSONObject formData, String key,
                Collection<? extends Descriptor<T>> descriptors) throws FormException {

        List<T> items = new ArrayList<T>();

        if(!formData.has(key))   return items;
        JSONArray a = JSONArray.fromObject(formData.get(key));

        for (Object o : a) {
            JSONObject jo = (JSONObject)o;
            String kind = jo.getString("kind");
            items.add(find(descriptors,kind).newInstance(req,jo));
        }

        return items;
    }

    /**
     * Finds a descriptor from a collection by its class name.
     */
    public static <T extends Descriptor> T find(Collection<? extends T> list, String className) {
        for (T d : list) {
            if(d.getClass().getName().equals(className))
                return d;
        }
        return null;
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

    /**
     * All the live instances of {@link Descriptor}.
     * {@link Descriptor}s are all supposed to have the singleton semantics, so
     * this shouldn't cause a memory leak. 
     */
    public static final CopyOnWriteList<Descriptor> ALL = new CopyOnWriteList<Descriptor>();

    /**
     * Used in {@link #checkMethods} to indicate that there's no check method.
     */
    private static final Method NONE;

    static {
        try {
            NONE = Object.class.getMethod("toString");
        } catch (NoSuchMethodException e) {
            throw new AssertionError();
        }
    }
}
