/*
 * The MIT License
 * 
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi
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

import hudson.DescriptorExtensionList;
import hudson.PluginWrapper;
import hudson.RelativePath;
import hudson.XmlFile;
import hudson.BulkChange;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.listeners.SaveableListener;
import hudson.util.FormApply;
import hudson.util.FormValidation.CheckMethod;
import hudson.util.ReflectionUtils;
import hudson.util.ReflectionUtils.Parameter;
import hudson.views.ListViewColumn;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.jelly.JellyCompatibleFacet;
import org.kohsuke.stapler.lang.Klass;
import org.springframework.util.StringUtils;
import org.jvnet.tiger_types.Types;
import org.apache.commons.io.IOUtils;

import static hudson.util.QuotedStringTokenizer.*;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import javax.servlet.ServletException;
import javax.servlet.RequestDispatcher;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
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
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

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
     * The class being described by this descriptor.
     */
    public transient final Class<? extends T> clazz;

    private transient final Map<String,CheckMethod> checkMethods = new ConcurrentHashMap<String,CheckMethod>();

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
        public final String displayName;

        PropertyType(Class clazz, Type type, String displayName) {
            this.clazz = clazz;
            this.type = type;
            this.displayName = displayName;
        }

        PropertyType(Field f) {
            this(f.getType(),f.getGenericType(),f.toString());
        }

        PropertyType(Method getter) {
            this(getter.getReturnType(),getter.getGenericReturnType(),getter.toString());
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
            return Jenkins.getInstance().getDescriptor(getItemType());
        }

        public Descriptor getItemTypeDescriptorOrDie() {
            Class it = getItemType();
            if (it == null) {
                throw new AssertionError(clazz + " is not an array/collection type in " + displayName + ". See https://wiki.jenkins-ci.org/display/JENKINS/My+class+is+missing+descriptor");
            }
            Descriptor d = Jenkins.getInstance().getDescriptor(it);
            if (d==null)
                throw new AssertionError(it +" is missing its descriptor in "+displayName+". See https://wiki.jenkins-ci.org/display/JENKINS/My+class+is+missing+descriptor");
            return d;
        }

        /**
         * Returns all the descriptors that produce types assignable to the property type.
         */
        public List<? extends Descriptor> getApplicableDescriptors() {
            return Jenkins.getInstance().getDescriptorList(clazz);
        }

        /**
         * Returns all the descriptors that produce types assignable to the item type for a collection property.
         */
        public List<? extends Descriptor> getApplicableItemDescriptors() {
            return Jenkins.getInstance().getDescriptorList(getItemType());
        }
    }

    /**
     * Help file redirect, keyed by the field name to the path.
     *
     * @see #getHelpFile(String) 
     */
    private transient final Map<String,HelpRedirect> helpRedirect = new HashMap<String,HelpRedirect>();

    private static class HelpRedirect {
        private final Class<? extends Describable> owner;
        private final String fieldNameToRedirectTo;

        private HelpRedirect(Class<? extends Describable> owner, String fieldNameToRedirectTo) {
            this.owner = owner;
            this.fieldNameToRedirectTo = fieldNameToRedirectTo;
        }

        private String resolve() {
            // the resolution has to be deferred to avoid ordering issue among descriptor registrations.
            return Jenkins.getInstance().getDescriptor(owner).getHelpFile(fieldNameToRedirectTo);
        }
    }

    /**
     *
     * @param clazz
     *      Pass in {@link #self()} to have the descriptor describe itself,
     *      (this hack is needed since derived types can't call "getClass()" to refer to itself.
     */
    protected Descriptor(Class<? extends T> clazz) {
        if (clazz==self())
            clazz = (Class)getClass();
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
     * Uniquely identifies this {@link Descriptor} among all the other {@link Descriptor}s.
     *
     * <p>
     * Historically {@link #clazz} is assumed to be unique, so this method uses that as the default,
     * but if you are adding {@link Descriptor}s programmatically for the same type, you can change
     * this to disambiguate them.
     *
     * <p>
     * To look up {@link Descriptor} from ID, use {@link Jenkins#getDescriptor(String)}.
     *
     * @return
     *      Stick to valid Java identifier character, plus '.', which had to be allowed for historical reasons.
     * 
     * @since 1.391
     */
    public String getId() {
        return clazz.getName();
    }

    /**
     * Unlike {@link #clazz}, return the parameter type 'T', which determines
     * the {@link DescriptorExtensionList} that this goes to.
     *
     * <p>
     * In those situations where subtypes cannot provide the type parameter,
     * this method can be overridden to provide it.
     */
    public Class<T> getT() {
        Type subTyping = Types.getBaseClass(getClass(), Descriptor.class);
        if (!(subTyping instanceof ParameterizedType)) {
            throw new IllegalStateException(getClass()+" doesn't extend Descriptor with a type parameter.");
        }
        return Types.erasure(Types.getTypeArgument(subTyping, 0));
    }

    /**
     * Gets the URL that this Descriptor is bound to, relative to the nearest {@link DescriptorByNameOwner}.
     * Since {@link Jenkins} is a {@link DescriptorByNameOwner}, there's always one such ancestor to any request.
     */
    public String getDescriptorUrl() {
        return "descriptorByName/"+getId();
    }

    /**
     * Gets the URL that this Descriptor is bound to, relative to the context path.
     * @since 1.406
     */
    public final String getDescriptorFullUrl() {
        return getCurrentDescriptorByNameUrl()+'/'+getDescriptorUrl();
    }

    /**
     * @since 1.402
     */
    public static String getCurrentDescriptorByNameUrl() {
        StaplerRequest req = Stapler.getCurrentRequest();

        // this override allows RenderOnDemandClosure to preserve the proper value
        Object url = req.getAttribute("currentDescriptorByNameUrl");
        if (url!=null)  return url.toString();

        Ancestor a = req.findAncestor(DescriptorByNameOwner.class);
        return a.getUrl();
    }

    /**
     * @deprecated since 1.528
     *      Use {@link #getCheckMethod(String)}
     */
    public String getCheckUrl(String fieldName) {
        return getCheckMethod(fieldName).toCheckUrl();
    }

    /**
     * If the field "xyz" of a {@link Describable} has the corresponding "doCheckXyz" method,
     * return the model of the check method.
     * <p>
     * This method is used to hook up the form validation method to the corresponding HTML input element.
     */
    public CheckMethod getCheckMethod(String fieldName) {
        CheckMethod method = checkMethods.get(fieldName);
        if(method==null) {
            method = new CheckMethod(this,fieldName);
            checkMethods.put(fieldName,method);
        }

        return method;
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

                RelativePath rp = p.annotation(RelativePath.class);
                if (rp!=null)
                    name = rp.value()+'/'+name;

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
     * Computes the auto-completion setting
     */
    public void calcAutoCompleteSettings(String field, Map<String,Object> attributes) {
        String capitalizedFieldName = StringUtils.capitalize(field);
        String methodName = "doAutoComplete" + capitalizedFieldName;
        Method method = ReflectionUtils.getPublicMethodNamed(getClass(), methodName);
        if(method==null)
            return;    // no auto-completion

        attributes.put("autoCompleteUrl", String.format("%s/%s/autoComplete%s", getCurrentDescriptorByNameUrl(), getDescriptorUrl(), capitalizedFieldName));
    }

    /**
     * Used by Jelly to abstract away the handlign of global.jelly vs config.jelly databinding difference.
     */
    public @CheckForNull PropertyType getPropertyType(@Nonnull Object instance, @Nonnull String field) {
        // in global.jelly, instance==descriptor
        return instance==this ? getGlobalPropertyType(field) : getPropertyType(field);
    }

    /**
     * Akin to {@link #getPropertyType(Object,String)} but never returns null.
     * @throws AssertionError in case the field cannot be found
     * @since 1.492
     */
    public @Nonnull PropertyType getPropertyTypeOrDie(@Nonnull Object instance, @Nonnull String field) {
        PropertyType propertyType = getPropertyType(instance, field);
        if (propertyType != null) {
            return propertyType;
        } else if (instance == this) {
            throw new AssertionError(getClass().getName() + " has no property " + field);
        } else {
            throw new AssertionError(clazz.getName() + " has no property " + field);
        }
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
        return getId().replace('.','-');
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
     *      See http://wiki.jenkins-ci.org/display/JENKINS/Structured+Form+Submission
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
                return verifyNewInstance(newInstance(req));
            } else {
                if (req==null) {
                    // yes, req is supposed to be always non-null, but see the note above
                    return verifyNewInstance(clazz.newInstance());
                }

                // new behavior as of 1.206
                return verifyNewInstance(req.bindJSON(clazz,formData));
            }
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e); // impossible
        } catch (InstantiationException e) {
            throw new Error("Failed to instantiate "+clazz+" from "+formData,e);
        } catch (IllegalAccessException e) {
            throw new Error("Failed to instantiate "+clazz+" from "+formData,e);
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to instantiate "+clazz+" from "+formData,e);
        }
    }

    /**
     * Look out for a typical error a plugin developer makes.
     * See http://hudson.361315.n4.nabble.com/Help-Hint-needed-Post-build-action-doesn-t-stay-activated-td2308833.html
     */
    private T verifyNewInstance(T t) {
        if (t!=null && t.getDescriptor()!=this) {
            // TODO: should this be a fatal error?
            LOGGER.warning("Father of "+ t+" and its getDescriptor() points to two different instances. Probably malplaced @Extension. See http://hudson.361315.n4.nabble.com/Help-Hint-needed-Post-build-action-doesn-t-stay-activated-td2308833.html");
        }
        return t;
    }

    /**
     * Returns the {@link Klass} object used for the purpose of loading resources from this descriptor.
     *
     * This hook enables other JVM languages to provide more integrated lookup.
     */
    public Klass<?> getKlass() {
        return Klass.java(clazz);
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
        return getHelpFile(getKlass(),fieldName);
    }

    public String getHelpFile(Klass<?> clazz, String fieldName) {
        HelpRedirect r = helpRedirect.get(fieldName);
        if (r!=null)    return r.resolve();

        for (Klass<?> c : clazz.getAncestors()) {
            String page = "/descriptor/" + getId() + "/help";
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

            if(getStaticHelpUrl(c, suffix) !=null)    return page;
        }
        return null;
    }
    
    /**
     * Tells Jenkins that the help file for the field 'fieldName' is defined in the help file for
     * the 'fieldNameToRedirectTo' in the 'owner' class.
     * @since 1.425
     */
    protected void addHelpFileRedirect(String fieldName, Class<? extends Describable> owner, String fieldNameToRedirectTo) {
        helpRedirect.put(fieldName, new HelpRedirect(owner,fieldNameToRedirectTo));
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
     *      See http://wiki.jenkins-ci.org/display/JENKINS/Structured+Form+Submission
     * @return false
     *      to keep the client in the same config page.
     */
    public boolean configure( StaplerRequest req, JSONObject json ) throws FormException {
        // compatibility
        return configure(req);
    }

    public String getConfigPage() {
        return getViewPage(clazz, getPossibleViewNames("config"), "config.jelly");
    }

    public String getGlobalConfigPage() {
        return getViewPage(clazz, getPossibleViewNames("global"), null);
    }
    
    private String getViewPage(Class<?> clazz, String pageName, String defaultValue) {
        return getViewPage(clazz,Collections.singleton(pageName),defaultValue);
    }

    private String getViewPage(Class<?> clazz, Collection<String> pageNames, String defaultValue) {
        while(clazz!=Object.class && clazz!=null) {
            for (String pageName : pageNames) {
                String name = clazz.getName().replace('.', '/').replace('$', '/') + "/" + pageName;
                if(clazz.getClassLoader().getResource(name)!=null)
                    return '/'+name;
            }
            clazz = clazz.getSuperclass();
        }
        return defaultValue;
    }

    protected final String getViewPage(Class<?> clazz, String pageName) {
        // We didn't find the configuration page.
        // Either this is non-fatal, in which case it doesn't matter what string we return so long as
        // it doesn't exist.
        // Or this error is fatal, in which case we want the developer to see what page he's missing.
        // so we put the page name.
        return getViewPage(clazz,pageName,pageName);
    }

    protected List<String> getPossibleViewNames(String baseName) {
        List<String> names = new ArrayList<String>();
        for (Facet f : WebApp.get(Jenkins.getInstance().servletContext).facets) {
            if (f instanceof JellyCompatibleFacet) {
                JellyCompatibleFacet jcf = (JellyCompatibleFacet) f;
                for (String ext : jcf.getScriptExtensions())
                    names.add(baseName +ext);
            }
        }
        return names;
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

    protected XmlFile getConfigFile() {
        return new XmlFile(new File(Jenkins.getInstance().getRootDir(),getId()+".xml"));
    }

    /**
     * Returns the plugin in which this descriptor is defined.
     *
     * @return
     *      null to indicate that this descriptor came from the core.
     */
    protected PluginWrapper getPlugin() {
        return Jenkins.getInstance().getPluginManager().whichPlugin(clazz);
    }

    /**
     * Serves <tt>help.html</tt> from the resource of {@link #clazz}.
     */
    public void doHelp(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        String path = req.getRestOfPath();
        if(path.contains("..")) throw new ServletException("Illegal path: "+path);

        path = path.replace('/','-');

        PluginWrapper pw = getPlugin();
        if (pw!=null) {
            rsp.setHeader("X-Plugin-Short-Name",pw.getShortName());
            rsp.setHeader("X-Plugin-Long-Name",pw.getLongName());
            rsp.setHeader("X-Plugin-From", Messages.Descriptor_From(
                    pw.getLongName().replace("Hudson","Jenkins").replace("hudson","jenkins"), pw.getUrl()));
        }

        for (Klass<?> c= getKlass(); c!=null; c=c.getSuperClass()) {
            RequestDispatcher rd = Stapler.getCurrentRequest().getView(c, "help"+path);
            if(rd!=null) {// template based help page
                rd.forward(req,rsp);
                return;
            }

            URL url = getStaticHelpUrl(c, path);
            if(url!=null) {
                // TODO: generalize macro expansion and perhaps even support JEXL
                rsp.setContentType("text/html;charset=UTF-8");
                InputStream in = url.openStream();
                try {
                    String literal = IOUtils.toString(in,"UTF-8");
                    rsp.getWriter().println(Util.replaceMacro(literal, Collections.singletonMap("rootURL",req.getContextPath())));
                } finally {
                    IOUtils.closeQuietly(in);
                }
                return;
            }
        }
        rsp.sendError(SC_NOT_FOUND);
    }

    private URL getStaticHelpUrl(Klass<?> c, String suffix) {
        Locale locale = Stapler.getCurrentRequest().getLocale();

        String base = "help"+suffix;

        URL url;
        url = c.getResource(base + '_' + locale.getLanguage() + '_' + locale.getCountry() + '_' + locale.getVariant() + ".html");
        if(url!=null)    return url;
        url = c.getResource(base + '_' + locale.getLanguage() + '_' + locale.getCountry() + ".html");
        if(url!=null)    return url;
        url = c.getResource(base + '_' + locale.getLanguage() + ".html");
        if(url!=null)    return url;

        // default
        return c.getResource(base + ".html");
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

        return newInstancesFromHeteroList(req,formData.get(key),descriptors);
    }

    public static <T extends Describable<T>>
    List<T> newInstancesFromHeteroList(StaplerRequest req, Object formData,
                Collection<? extends Descriptor<T>> descriptors) throws FormException {

        List<T> items = new ArrayList<T>();

        if (formData!=null) {
            for (Object o : JSONArray.fromObject(formData)) {
                JSONObject jo = (JSONObject)o;
                Descriptor<T> d = null;
                String kind = jo.optString("kind", null);
                if (kind != null) {
                    d = findById(descriptors, kind);
                }
                if (d == null) {
                  kind = jo.getString("$class");
                  d = findByClassName(descriptors, kind);
                }
                if (d != null) {
                    items.add(d.newInstance(req, jo));
                } else {
                    LOGGER.warning("Received unexpected formData for descriptor " + kind);
                }
            }
        }

        return items;
    }

    /**
     * Finds a descriptor from a collection by its id.
     */
    public static @CheckForNull <T extends Descriptor> T findById(Collection<? extends T> list, String id) {
        for (T d : list) {
            if(d.getId().equals(id))
                return d;
        }
        return null;
    }

    /**
     * Finds a descriptor from a collection by its class name.
     * @deprecated Since we introduced {@link Descriptor#getId()}, it is a preferred method of identifying descriptor by a string.
     */
    public static @CheckForNull <T extends Descriptor> T findByClassName(Collection<? extends T> list, String className) {
        for (T d : list) {
            if(d.getClass().getName().equals(className))
                return d;
        }
        return null;
    }

    /**
     * Finds a descriptor from a collection by its class name or ID.
     * @deprecated choose between {@link #findById(java.util.Collection, String)} or {@link #findByClassName(java.util.Collection, String)}
     */
    public static @CheckForNull <T extends Descriptor> T find(Collection<? extends T> list, String string) {
        T d = findByClassName(list, string);
        if (d != null) {
                return d;
        }
        return findById(list, string);
    }

    public static @CheckForNull Descriptor find(String className) {
        return find(ExtensionList.lookup(Descriptor.class),className);
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
            if (FormApply.isApply(req)) {
                FormApply.applyResponse("notificationBar.show(" + quote(getMessage())+ ",notificationBar.ERROR)")
                        .generateResponse(req, rsp, node);
            } else {
                // for now, we can't really use the field name that caused the problem.
                new Failure(getMessage()).generateResponse(req,rsp,node);
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(Descriptor.class.getName());

    /**
     * Special type indicating that {@link Descriptor} describes itself.
     * @see Descriptor#Descriptor(Class)
     */
    public static final class Self {}

    protected static Class self() { return Self.class; }
}
