/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
import hudson.diagnosis.OldDataMonitor;
import hudson.model.listeners.SaveableListener;
import hudson.util.ReflectionUtils;
import hudson.util.ReflectionUtils.Parameter;
import hudson.views.ListViewColumn;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.*;
import org.springframework.util.StringUtils;
import org.jvnet.tiger_types.Types;
import org.apache.commons.io.IOUtils;

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
 * Take the list view support as an example, which is implemented
 * in {@link ListView} class. Whenever a new view is created, a new
 * {@link ListView} instance is created with the configuration
 * information. This instance gets serialized to XML, and this instance
 * will be called to render the view page. This is the job
 * of {@link Describable} &mdash; each instance represents a specific
 * configuration of a view (what projects are in it, regular expression, etc.)
 *
 * <p>
 * For Hudson to create such configured {@link ListView} instance, Hudson
 * needs another object that captures the metadata of {@link ListView},
 * and that is what a {@link Descriptor} is for. {@link ListView} class
 * has a singleton descriptor, and this descriptor helps render
 * the configuration form, remember system-wide configuration, and works as a factory.
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
     * @deprecated since 2006-11-16
     */
    @Deprecated
    private transient Map<String,Object> properties;

    /**
     * The class being described by this descriptor.
     */
    public transient final Class<? extends T> clazz;

    private transient final Map<String,String> checkMethods = new ConcurrentHashMap<String,String>();

    /**
     * Lazily computed list of properties on {@link #clazz} and on the descriptor itself.
     */
    private transient volatile Map<String, PropertyType> propertyTypes,globalPropertyTypes;

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
            return Hudson.getInstance().getDescriptor(getItemType());
        }

        public Descriptor getItemTypeDescriptorOrDie() {
            return Hudson.getInstance().getDescriptorOrDie(getItemType());
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

        // detect a type error. this Descriptor is supposed to be returned from getDescriptor(), so make sure its type match up.
        // this prevents a bug like http://www.nabble.com/Creating-a-new-parameter-Type-%3A-Masked-Parameter-td24786554.html
        try {
            Method getd = clazz.getMethod("getDescriptor");
            if(!getd.getReturnType().isAssignableFrom(getClass())) {
                throw new AssertionError(getClass()+" must be assignable to "+getd.getReturnType());
            }
        } catch (NoSuchMethodException e) {
            throw new AssertionError(getClass()+" is missing getDescriptor method.");
        }

    }

    /**
     * Human readable name of this kind of configurable object.
     */
    public abstract String getDisplayName();

    /**
     * Gets the URL that this Descriptor is bound to, relative to the nearest {@link DescriptorByNameOwner}.
     * Since {@link Hudson} is a {@link DescriptorByNameOwner}, there's always one such ancestor to any request.
     */
    public String getDescriptorUrl() {
        return "descriptorByName/"+clazz.getName();
    }

    private String getCurrentDescriptorByNameUrl() {
        StaplerRequest req = Stapler.getCurrentRequest();
        Ancestor a = req.findAncestor(DescriptorByNameOwner.class);
        return a.getUrl();
    }

    /**
     * If the field "xyz" of a {@link Describable} has the corresponding "doCheckXyz" method,
     * return the form-field validation string. Otherwise null.
     * <p>
     * This method is used to hook up the form validation method to the corresponding HTML input element.
     */
    public String getCheckUrl(String fieldName) {
        String method = checkMethods.get(fieldName);
        if(method==null) {
            method = calcCheckUrl(fieldName);
            checkMethods.put(fieldName,method);
        }

        if (method.equals(NONE)) // == would do, but it makes IDE flag a warning
            return null;

        // put this under the right contextual umbrella.
        // a is always non-null because we already have Hudson as the sentinel
        return singleQuote(getCurrentDescriptorByNameUrl()+'/')+'+'+method;
    }

    private String calcCheckUrl(String fieldName) {
        String capitalizedFieldName = StringUtils.capitalize(fieldName);

        Method method = ReflectionUtils.getPublicMethodNamed(getClass(),"doCheck"+ capitalizedFieldName);

        if(method==null)
            return NONE;

        return singleQuote(getDescriptorUrl() +"/check"+capitalizedFieldName) + buildParameterList(method, new StringBuilder()).append(".toString()");
    }

    /**
     * Builds query parameter line by figuring out what should be submitted
     */
    private StringBuilder buildParameterList(Method method, StringBuilder query) {
        for (Parameter p : ReflectionUtils.getParameters(method)) {
            QueryParameter qp = p.annotation(QueryParameter.class);
            if (qp!=null) {
                String name = qp.value();
                if (name.length()==0) name = p.name();
                if (name==null || name.length()==0)
                    continue;   // unknown parameter name. we'll report the error when the form is submitted.

                if (query.length()==0)  query.append("+qs(this)");

                if (name.equals("value")) {
                    // The special 'value' parameter binds to the the current field
                    query.append(".addThis()");
                } else {
                    query.append(".nearBy('"+name+"')");
                }
                continue;
            }

            Method m = ReflectionUtils.getPublicMethodNamed(p.type(), "fromStapler");
            if (m!=null)    buildParameterList(m,query);
        }
        return query;
    }

    /**
     * Computes the list of other form fields that the given field depends on, via the doFillXyzItems method,
     * and sets that as the 'fillDependsOn' attribute. Also computes the URL of the doFillXyzItems and
     * sets that as the 'fillUrl' attribute.
     */
    public void calcFillSettings(String field, Map<String,Object> attributes) {
        String capitalizedFieldName = StringUtils.capitalize(field);
        String methodName = "doFill" + capitalizedFieldName + "Items";
        Method method = ReflectionUtils.getPublicMethodNamed(getClass(), methodName);
        if(method==null)
            throw new IllegalStateException(String.format("%s doesn't have the %s method for filling a drop-down list", getClass(), methodName));

        // build query parameter line by figuring out what should be submitted
        List<String> depends = buildFillDependencies(method, new ArrayList<String>());

        if (!depends.isEmpty())
            attributes.put("fillDependsOn",Util.join(depends," "));
        attributes.put("fillUrl", String.format("%s/%s/fill%sItems", getCurrentDescriptorByNameUrl(), getDescriptorUrl(), capitalizedFieldName));
    }

    private List<String> buildFillDependencies(Method method, List<String> depends) {
        for (Parameter p : ReflectionUtils.getParameters(method)) {
            QueryParameter qp = p.annotation(QueryParameter.class);
            if (qp!=null) {
                String name = qp.value();
                if (name.length()==0) name = p.name();
                if (name==null || name.length()==0)
                    continue;   // unknown parameter name. we'll report the error when the form is submitted.

                depends.add(name);
                continue;
            }

            Method m = ReflectionUtils.getPublicMethodNamed(p.type(), "fromStapler");
            if (m!=null)
                buildFillDependencies(m,depends);
        }
        return depends;
    }

    /**
     * Used by Jelly to abstract away the handlign of global.jelly vs config.jelly databinding difference.
     */
    public PropertyType getPropertyType(Object instance, String field) {
        // in global.jelly, instance==descriptor
        return instance==this ? getGlobalPropertyType(field) : getPropertyType(field);
    }

    /**
     * Obtains the property type of the given field of {@link #clazz}
     */
    public PropertyType getPropertyType(String field) {
        if(propertyTypes==null)
            propertyTypes = buildPropertyTypes(clazz);
        return propertyTypes.get(field);
    }

    /**
     * Obtains the property type of the given field of this descriptor.
     */
    public PropertyType getGlobalPropertyType(String field) {
        if(globalPropertyTypes==null)
            globalPropertyTypes = buildPropertyTypes(getClass());
        return globalPropertyTypes.get(field);
    }

    /**
     * Given the class, list up its {@link PropertyType}s from its public fields/getters.
     */
    private Map<String, PropertyType> buildPropertyTypes(Class<?> clazz) {
        Map<String, PropertyType> r = new HashMap<String, PropertyType>();
        for (Field f : clazz.getFields())
            r.put(f.getName(),new PropertyType(f));

        for (Method m : clazz.getMethods())
            if(m.getName().startsWith("get"))
                r.put(Introspector.decapitalize(m.getName().substring(3)),new PropertyType(m));

        return r;
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
     * <p>
     * For some types of {@link Describable}, such as {@link ListViewColumn}, this method
     * can be invoked with null request object for historical reason. Such design is considered
     * broken, but due to the compatibility reasons we cannot fix it. Because of this, the
     * default implementation gracefully handles null request, but the contract of the method
     * still is "request is always non-null." Extension points that need to define the "default instance"
     * semantics should define a descriptor subtype and add the no-arg newInstance method.
     *
     * @param req
     *      Always non-null (see note above.) This object includes represents the entire submission.
     * @param formData
     *      The JSON object that captures the configuration data for this {@link Descriptor}.
     *      See http://hudson.gotdns.com/wiki/display/HUDSON/Structured+Form+Submission
     *      Always non-null.
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
                if (req==null) {
                    // yes, req is supposed to be always non-null, but see the note above
                    return clazz.newInstance();
                }

                // new behavior as of 1.206
                return req.bindJSON(clazz,formData);
            }
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e); // impossible
        } catch (InstantiationException e) {
            throw new Error(e);
        } catch (IllegalAccessException e) {
            throw new Error(e);
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
     * Checks if the type represented by this descriptor is a subtype of the given type.
     */
    public final boolean isSubTypeOf(Class type) {
        return type.isAssignableFrom(clazz);
    }

    /**
     * @deprecated
     *      As of 1.239, use {@link #configure(StaplerRequest, JSONObject)}.
     */
    public boolean configure( StaplerRequest req ) throws FormException {
        return true;
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
            SaveableListener.fireOnChange(this, getConfigFile());
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
            file.unmarshal(this);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load "+file, e);
        }
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
        String base = c.getName().replace('.', '/').replace('$','/') + "/help"+suffix;

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

    public static final class FormException extends Exception implements HttpResponse {
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

        public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
            // for now, we can't really use the field name that caused the problem.
            new Failure(getMessage()).generateResponse(req,rsp,node);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(Descriptor.class.getName());

    /**
     * Used in {@link #checkMethods} to indicate that there's no check method.
     */
    private static final String NONE = "\u0000";

    private Object readResolve() {
        if (properties!=null)
            OldDataMonitor.report(this, "1.62");
        return this;
    }
}
