/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.model;

import hudson.XmlFile;
import hudson.BulkChange;
import hudson.Util;
import static hudson.Util.singleQuote;
import hudson.scm.CVSSCM;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.Ancestor;
import org.springframework.util.StringUtils;
import org.jvnet.tiger_types.Types;
import org.apache.commons.io.IOUtils;

import javax.servlet.http.HttpServletRequest;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import javax.servlet.ServletException;
import javax.servlet.RequestDispatcher;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.beans.Introspector;

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
 * <h2>Reflection Enhancement</h2>
 * {@link Descriptor} defines addition to the standard Java reflection
 * and provides reflective information about its corresponding {@link Describable}.
 * These are primarily used by tag libraries to
 * keep the Jelly scripts concise. 
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

    /**
     * Lazily computed list of properties on {@link #clazz}.
     */
    private transient volatile Map<String, PropertyType> propertyTypes;

    /**
     * Represents a readable property on {@link Describable}.
     */
    public static final class PropertyType {
        public final Class clazz;
        public final Type type;
        private volatile Class itemType;

        PropertyType(Class clazz, Type type) {
            this.clazz = clazz;
            this.type = type;
        }

        PropertyType(Field f) {
            this(f.getType(),f.getGenericType());
        }

        PropertyType(Method getter) {
            this(getter.getReturnType(),getter.getGenericReturnType());
        }

        public Enum[] getEnumConstants() {
            return (Enum[])clazz.getEnumConstants();
        }

        /**
         * If the property is a collection/array type, what is an item type?
         */
        public Class getItemType() {
            if(itemType==null)
                itemType = computeItemType();
            return itemType;
        }

        private Class computeItemType() {
            if(clazz.isArray()) {
                return clazz.getComponentType();
            }
            if(Collection.class.isAssignableFrom(clazz)) {
                Type col = Types.getBaseClass(type, Collection.class);

                if (col instanceof ParameterizedType)
                    return Types.erasure(Types.getTypeArgument(col,0));
                else
                    return Object.class;
            }
            return null;
        }

        /**
         * Returns {@link Descriptor} whose 'clazz' is the same as {@link #getItemType() the item type}.
         */
        public Descriptor getItemTypeDescriptor() {
            Class itemType = getItemType();
            for( Descriptor d : Hudson.getInstance().getExtensionList(Descriptor.class) )
                if(d.clazz==itemType)
                    return d;
            return null;

        }
    }

    protected Descriptor(Class<? extends T> clazz) {
        this.clazz = clazz;
        // doing this turns out to be very error prone,
        // as field initializers in derived types will override values.
        // load();
    }

    /**
     * Infers the type of the corresponding {@link Describable} from the outer class.
     * This version works when you follow the common convention, where a descriptor
     * is written as the static nested class of the describable class.
     * 
     * @since 1.278
     */
    protected Descriptor() {
        this.clazz = (Class<T>)getClass().getEnclosingClass();
        if(clazz==null)
            throw new AssertionError(getClass()+" doesn't have an outer class. Use the constructor that takes the Class object explicitly.");

        // detect an type error
        Type bt = Types.getBaseClass(getClass(), Descriptor.class);
        if (bt instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) bt;
            // this 't' is the closest approximation of T of Descriptor<T>.
            Class t = Types.erasure(pt.getActualTypeArguments()[0]);
            if(!t.isAssignableFrom(clazz))
                throw new AssertionError("Outer class "+clazz+" of "+getClass()+" is not assignable to "+t+". Perhaps wrong outer class?");
        }
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

        StaplerRequest req = Stapler.getCurrentRequest();
        Ancestor a = req.findAncestor(DescriptorByNameOwner.class);
        // a is always non-null because we already have Hudson as the sentinel
        return singleQuote(a.getUrl()+"/descriptorByName/"+clazz.getName()+"/check"+capitalizedFieldName+"?value=")+"+toValue(this)";
    }

    /**
     * Obtains the property type of the given field of {@link #clazz}
     */
    public PropertyType getPropertyType(String field) {
        if(propertyTypes ==null) {
            Map<String, PropertyType> r = new HashMap<String, PropertyType>();
            for (Field f : clazz.getFields())
                r.put(f.getName(),new PropertyType(f));

            for (Method m : clazz.getMethods())
                if(m.getName().startsWith("get"))
                    r.put(Introspector.decapitalize(m.getName().substring(3)),new PropertyType(m));

            propertyTypes = r;
        }
        return propertyTypes.get(field);
    }

    /**
     * Gets the class name nicely escaped to be usable as a key in the structured form submission.
     */
    public final String getJsonSafeClassName() {
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
     * Starting 1.282, this method uses "convention over configuration" &mdash; you should
     * just put the "help.html" (and its localized versions, if any) in the same directory
     * you put your Jelly view files, and this method will automatically does the right thing.
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
        return getHelpFile(null);
    }

    /**
     * Returns the path to the help screen HTML for the given field.
     *
     * <p>
     * The help files are assumed to be at "help/FIELDNAME.html" with possible
     * locale variations.
     */
    public String getHelpFile(final String fieldName) {
        for(Class c=clazz; c!=null; c=c.getSuperclass()) {
            String page = "/descriptor/" + clazz.getName() + "/help";
            String suffix;
            if(fieldName==null) {
                suffix="";
            } else {
                page += '/'+fieldName;
                suffix='-'+fieldName;
            }

            try {
                if(Stapler.getCurrentRequest().getView(c,"help"+suffix)!=null)
                    return page;
            } catch (IOException e) {
                throw new Error(e);
            }

            InputStream in = getHelpStream(c,suffix);
            IOUtils.closeQuietly(in);
            if(in!=null)    return page;
        }
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
        // We didn't find the configuration page.
        // Either this is non-fatal, in which case it doesn't matter what string we return so long as
        // it doesn't exist.
        // Or this error is fatal, in which case we want the developer to see what page he's missing.
        // so we put the page name.
        return pageName;
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

    /**
     * Serves <tt>help.html</tt> from the resource of {@link #clazz}.
     */
    public void doHelp(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        String path = req.getRestOfPath();
        if(path.contains("..")) throw new ServletException("Illegal path: "+path);

        path = path.replace('/','-');

        for (Class c=clazz; c!=null; c=c.getSuperclass()) {
            RequestDispatcher rd = Stapler.getCurrentRequest().getView(c, "help"+path);
            if(rd!=null) {// Jelly-generated help page
                rd.forward(req,rsp);
                return;
            }

            InputStream in = getHelpStream(c,path);
            if(in!=null) {
                // TODO: generalize macro expansion and perhaps even support JEXL
                rsp.setContentType("text/html;charset=UTF-8");
                String literal = IOUtils.toString(in,"UTF-8");
                rsp.getWriter().println(Util.replaceMacro(literal, Collections.singletonMap("rootURL",req.getContextPath())));
                in.close();
                return;
            }
        }
        rsp.sendError(SC_NOT_FOUND);
    }

    private InputStream getHelpStream(Class c, String suffix) {
        Locale locale = Stapler.getCurrentRequest().getLocale();
        String base = c.getName().replace('.', '/') + "/help"+suffix;

        ClassLoader cl = c.getClassLoader();
        if(cl==null)    return null;
        
        InputStream in;
        in = cl.getResourceAsStream(base + '_' + locale.getLanguage() + '_' + locale.getCountry() + '_' + locale.getVariant() + ".html");
        if(in!=null)    return in;
        in = cl.getResourceAsStream(base + '_' + locale.getLanguage() + '_' + locale.getCountry() + ".html");
        if(in!=null)    return in;
        in = cl.getResourceAsStream(base + '_' + locale.getLanguage() + ".html");
        if(in!=null)    return in;

        // default
        return cl.getResourceAsStream(base+".html");
    }


//
// static methods
//


    // to work around warning when creating a generic array type
    public static <T> T[] toArray( T... values ) {
        return values;
    }

    public static <T> List<T> toList( T... values ) {
        return new ArrayList<T>(Arrays.asList(values));
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

    public static Descriptor find(String className) {
        return find(Hudson.getInstance().getExtensionList(Descriptor.class),className);
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