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

import static hudson.util.QuotedStringTokenizer.quote;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.BulkChange;
import hudson.DescriptorExtensionList;
import hudson.ExtensionList;
import hudson.PluginWrapper;
import hudson.RelativePath;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.listeners.SaveableListener;
import hudson.security.Permission;
import hudson.util.FormApply;
import hudson.util.FormValidation.CheckMethod;
import hudson.util.ReflectionUtils;
import hudson.util.ReflectionUtils.Parameter;
import hudson.views.ListViewColumn;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import java.beans.Introspector;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import jenkins.model.Loadable;
import jenkins.security.RedactSecretJsonInErrorMessageSanitizer;
import jenkins.security.stapler.StaplerNotDispatchable;
import jenkins.util.io.OnMaster;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.jvnet.tiger_types.Types;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.BindInterceptor;
import org.kohsuke.stapler.Facet;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.WebApp;
import org.kohsuke.stapler.jelly.JellyCompatibleFacet;
import org.kohsuke.stapler.lang.Klass;

/**
 * Metadata about a configurable instance.
 *
 * <p>
 * {@link Descriptor} is an object that has metadata about a {@link Describable}
 * object, and also serves as a factory (in a way this relationship is similar
 * to {@link Object}/{@link Class} relationship.
 *
 * A {@link Descriptor}/{@link Describable}
 * combination is used throughout in Jenkins to implement a
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
 * For Jenkins to create such configured {@link ListView} instance, Jenkins
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
 * {@link #load()} is automatically invoked as a JSR-250 lifecycle method if derived class
 * do implement {@link PersistentDescriptor}.
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
public abstract class Descriptor<T extends Describable<T>> implements Loadable, Saveable, OnMaster {
    /**
     * The class being described by this descriptor.
     */
    public final transient Class<? extends T> clazz;

    private final transient Map<String, CheckMethod> checkMethods = new ConcurrentHashMap<>(2);

    /**
     * Lazily computed list of properties on {@link #clazz} and on the descriptor itself.
     */
    private transient volatile Map<String, PropertyType> propertyTypes, globalPropertyTypes;

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
            this(f.getType(), f.getGenericType(), f.toString());
        }

        PropertyType(Method getter) {
            this(getter.getReturnType(), getter.getGenericReturnType(), getter.toString());
        }

        public Enum[] getEnumConstants() {
            return (Enum[]) clazz.getEnumConstants();
        }

        /**
         * If the property is a collection/array type, what is an item type?
         */
        public Class getItemType() {
            if (itemType == null)
                itemType = computeItemType();
            return itemType;
        }

        private Class computeItemType() {
            if (clazz.isArray()) {
                return clazz.getComponentType();
            }
            if (Collection.class.isAssignableFrom(clazz)) {
                Type col = Types.getBaseClass(type, Collection.class);

                if (col instanceof ParameterizedType)
                    return Types.erasure(Types.getTypeArgument(col, 0));
                else
                    return Object.class;
            }
            return null;
        }

        /**
         * Returns {@link Descriptor} whose 'clazz' is the same as {@link #getItemType() the item type}.
         */
        public Descriptor getItemTypeDescriptor() {
            return Jenkins.get().getDescriptor(getItemType());
        }

        public Descriptor getItemTypeDescriptorOrDie() {
            Class it = getItemType();
            if (it == null) {
                throw new AssertionError(clazz + " is not an array/collection type in " + displayName + ". See https://www.jenkins.io/redirect/developer/class-is-missing-descriptor");
            }
            Descriptor d = Jenkins.get().getDescriptor(it);
            if (d == null)
                throw new AssertionError(it + " is missing its descriptor in " + displayName + ". See https://www.jenkins.io/redirect/developer/class-is-missing-descriptor");
            return d;
        }

        /**
         * Returns all the descriptors that produce types assignable to the property type.
         */
        public List<? extends Descriptor> getApplicableDescriptors() {
            return Jenkins.get().getDescriptorList(clazz);
        }

        /**
         * Returns all the descriptors that produce types assignable to the item type for a collection property.
         */
        public List<? extends Descriptor> getApplicableItemDescriptors() {
            return Jenkins.get().getDescriptorList(getItemType());
        }
    }

    /**
     * Help file redirect, keyed by the field name to the path.
     *
     * @see #getHelpFile(String)
     */
    private final transient Map<String, HelpRedirect> helpRedirect = new HashMap<>(2);

    private static class HelpRedirect {
        private final Class<? extends Describable> owner;
        private final String fieldNameToRedirectTo;

        private HelpRedirect(Class<? extends Describable> owner, String fieldNameToRedirectTo) {
            this.owner = owner;
            this.fieldNameToRedirectTo = fieldNameToRedirectTo;
        }

        private String resolve() {
            // the resolution has to be deferred to avoid ordering issue among descriptor registrations.
            return Jenkins.get().getDescriptor(owner).getHelpFile(fieldNameToRedirectTo);
        }
    }

    /**
     *
     * @param clazz
     *      Pass in {@link #self()} to have the descriptor describe itself,
     *      (this hack is needed since derived types can't call "getClass()" to refer to itself.
     */
    protected Descriptor(Class<? extends T> clazz) {
        if (clazz == self())
            clazz = (Class) getClass();
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
        this.clazz = (Class<T>) getClass().getEnclosingClass();
        if (clazz == null)
            throw new AssertionError(getClass() + " doesn't have an outer class. Use the constructor that takes the Class object explicitly.");

        // detect an type error
        Type bt = Types.getBaseClass(getClass(), Descriptor.class);
        if (bt instanceof ParameterizedType pt) {
            // this 't' is the closest approximation of T of Descriptor<T>.
            Class t = Types.erasure(pt.getActualTypeArguments()[0]);
            if (!t.isAssignableFrom(clazz))
                throw new AssertionError("Outer class " + clazz + " of " + getClass() + " is not assignable to " + t + ". Perhaps wrong outer class?");
        }

        // detect a type error. this Descriptor is supposed to be returned from getDescriptor(), so make sure its type match up.
        // this prevents a bug like http://www.nabble.com/Creating-a-new-parameter-Type-%3A-Masked-Parameter-td24786554.html
        try {
            Method getd = clazz.getMethod("getDescriptor");
            if (!getd.getReturnType().isAssignableFrom(getClass())) {
                throw new AssertionError(getClass() + " must be assignable to " + getd.getReturnType());
            }
        } catch (NoSuchMethodException e) {
            throw new AssertionError(getClass() + " is missing getDescriptor method.", e);
        }

    }

    /**
     * Human readable name of this kind of configurable object.
     * Should be overridden for most descriptors, if the display name is visible somehow.
     * As a fallback it uses {@link Class#getSimpleName} on {@link #clazz}, so for example {@code MyThing} from {@code some.pkg.MyThing.DescriptorImpl}.
     * Historically some implementations returned null as a way of hiding the descriptor from the UI,
     * but this is generally managed by an explicit method such as {@code isEnabled} or {@code isApplicable}.
     */
    @NonNull
    public String getDisplayName() {
        return clazz.getSimpleName();
    }

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
            throw new IllegalStateException(getClass() + " doesn't extend Descriptor with a type parameter.");
        }
        return Types.erasure(Types.getTypeArgument(subTyping, 0));
    }

    /**
     * Gets the URL that this Descriptor is bound to, relative to the nearest {@link DescriptorByNameOwner}.
     * Since {@link Jenkins} is a {@link DescriptorByNameOwner}, there's always one such ancestor to any request.
     */
    public String getDescriptorUrl() {
        return "descriptorByName/" + getId();
    }

    /**
     * Gets the URL that this Descriptor is bound to, relative to the context path.
     * @since 1.406
     */
    public final String getDescriptorFullUrl() {
        return getCurrentDescriptorByNameUrl() + '/' + getDescriptorUrl();
    }

    /**
     * @since 1.402
     */
    public static String getCurrentDescriptorByNameUrl() {
        StaplerRequest2 req = Stapler.getCurrentRequest2();

        // this override allows RenderOnDemandClosure to preserve the proper value
        Object url = req.getAttribute("currentDescriptorByNameUrl");
        if (url != null)  return url.toString();

        Ancestor a = req.findAncestor(DescriptorByNameOwner.class);
        return a.getUrl();
    }

    /**
     * @deprecated since 1.528
     *      Use {@link #getCheckMethod(String)}
     */
    @Deprecated
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
        if (method == null) {
            method = new CheckMethod(this, fieldName);
            checkMethods.put(fieldName, method);
        }

        return method;
    }

    /**
     * Computes the list of other form fields that the given field depends on, via the doFillXyzItems method,
     * and sets that as the 'fillDependsOn' attribute. Also computes the URL of the doFillXyzItems and
     * sets that as the 'fillUrl' attribute.
     */
    public void calcFillSettings(String field, Map<String, Object> attributes) {
        String capitalizedFieldName = field == null || field.isEmpty() ? field : Character.toTitleCase(field.charAt(0)) + field.substring(1);
        String methodName = "doFill" + capitalizedFieldName + "Items";
        Method method = ReflectionUtils.getPublicMethodNamed(getClass(), methodName);
        if (method == null)
            throw new IllegalStateException(String.format("%s doesn't have the %s method for filling a drop-down list", getClass(), methodName));

        // build query parameter line by figuring out what should be submitted
        List<String> depends = buildFillDependencies(method, new ArrayList<>());

        if (!depends.isEmpty())
            attributes.put("fillDependsOn", String.join(" ", depends));
        attributes.put("fillUrl", String.format("%s/%s/fill%sItems", getCurrentDescriptorByNameUrl(), getDescriptorUrl(), capitalizedFieldName));
    }

    private List<String> buildFillDependencies(Method method, List<String> depends) {
        for (Parameter p : ReflectionUtils.getParameters(method)) {
            QueryParameter qp = p.annotation(QueryParameter.class);
            if (qp != null) {
                String name = qp.value();
                if (name.isEmpty()) name = p.name();
                if (name == null || name.isEmpty())
                    continue;   // unknown parameter name. we'll report the error when the form is submitted.

                RelativePath rp = p.annotation(RelativePath.class);
                if (rp != null)
                    name = rp.value() + '/' + name;

                depends.add(name);
                continue;
            }

            Method m = ReflectionUtils.getPublicMethodNamed(p.type(), "fromStapler");
            if (m != null)
                buildFillDependencies(m, depends);
        }
        return depends;
    }

    /**
     * Computes the auto-completion setting
     */
    public void calcAutoCompleteSettings(String field, Map<String, Object> attributes) {
        String capitalizedFieldName = field == null || field.isEmpty() ? field : Character.toTitleCase(field.charAt(0)) + field.substring(1);
        String methodName = "doAutoComplete" + capitalizedFieldName;
        Method method = ReflectionUtils.getPublicMethodNamed(getClass(), methodName);
        if (method == null)
            return;    // no auto-completion

        attributes.put("autoCompleteUrl", String.format("%s/%s/autoComplete%s", getCurrentDescriptorByNameUrl(), getDescriptorUrl(), capitalizedFieldName));
    }

    /**
     * Used by Jelly to abstract away the handling of global.jelly vs config.jelly databinding difference.
     */
    public @CheckForNull PropertyType getPropertyType(@NonNull Object instance, @NonNull String field) {
        // in global.jelly, instance==descriptor
        return instance == this ? getGlobalPropertyType(field) : getPropertyType(field);
    }

    /**
     * Akin to {@link #getPropertyType(Object,String)} but never returns null.
     * @throws AssertionError in case the field cannot be found
     * @since 1.492
     */
    public @NonNull PropertyType getPropertyTypeOrDie(@NonNull Object instance, @NonNull String field) {
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
        if (propertyTypes == null)
            propertyTypes = buildPropertyTypes(clazz);
        return propertyTypes.get(field);
    }

    /**
     * Obtains the property type of the given field of this descriptor.
     */
    public PropertyType getGlobalPropertyType(String field) {
        if (globalPropertyTypes == null)
            globalPropertyTypes = buildPropertyTypes(getClass());
        return globalPropertyTypes.get(field);
    }

    /**
     * Given the class, list up its {@link PropertyType}s from its public fields/getters.
     */
    private Map<String, PropertyType> buildPropertyTypes(Class<?> clazz) {
        Map<String, PropertyType> r = new HashMap<>();
        for (Field f : clazz.getFields())
            r.put(f.getName(), new PropertyType(f));

        for (Method m : clazz.getMethods())
            if (m.getName().startsWith("get"))
                r.put(Introspector.decapitalize(m.getName().substring(3)), new PropertyType(m));

        return r;
    }

    /**
     * Gets the class name nicely escaped to be usable as a key in the structured form submission.
     */
    public final String getJsonSafeClassName() {
        return getId().replace('.', '-');
    }

    /**
     * @deprecated
     *      Implement {@link #newInstance(StaplerRequest, JSONObject)} method instead.
     *      Deprecated as of 1.145.
     */
    @Deprecated
    public T newInstance(StaplerRequest req) throws FormException {
        throw new UnsupportedOperationException(getClass() + " should implement newInstance(StaplerRequest,JSONObject)");
    }

    /**
     * Creates a configured instance from the submitted form.
     *
     * <p>
     * Hudson only invokes this method when the user wants an instance of {@code T}.
     * So there's no need to check that in the implementation.
     *
     * <p>
     * The default implementation of this method uses {@link #bindJSON}
     * which performs the databinding on the constructor of {@link #clazz}.
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
     *      See <a href="https://www.jenkins.io/doc/developer/forms/structured-form-submission/">the developer documentation</a>.
     *      Always non-null.
     *
     * @throws FormException
     *      Signals a problem in the submitted form.
     * @since TODO
     */
    public T newInstance(@Nullable StaplerRequest2 req, @NonNull JSONObject formData) throws FormException {
        if (Util.isOverridden(Descriptor.class, getClass(), "newInstance", StaplerRequest.class, JSONObject.class)) {
            return newInstance(req != null ? StaplerRequest.fromStaplerRequest2(req) : null, formData);
        } else {
            return newInstanceImpl(req, formData);
        }
    }

    /**
     * @deprecated use {@link #newInstance(StaplerRequest2, JSONObject)}
     * @since 1.145
     */
    @Deprecated
    public T newInstance(@Nullable StaplerRequest req, @NonNull JSONObject formData) throws FormException {
        return newInstanceImpl(req != null ? StaplerRequest.toStaplerRequest2(req) : null, formData);
    }

    private T newInstanceImpl(@Nullable StaplerRequest2 req, @NonNull JSONObject formData) throws FormException {
        try {
            Method m = getClass().getMethod("newInstance", StaplerRequest.class);

            if (!Modifier.isAbstract(m.getDeclaringClass().getModifiers())) {
                // this class overrides newInstance(StaplerRequest).
                // maintain the backward compatible behavior
                return verifyNewInstance(newInstance(StaplerRequest.fromStaplerRequest2(req)));
            } else {
                if (req == null) {
                    // yes, req is supposed to be always non-null, but see the note above
                    return verifyNewInstance(clazz.getDeclaredConstructor().newInstance());
                }
                return verifyNewInstance(bindJSON(req, clazz, formData, true));
            }
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException | RuntimeException e) {
            if (e instanceof RuntimeException && e instanceof HttpResponse) {
                throw (RuntimeException) e;
            }
            throw new LinkageError("Failed to instantiate " + clazz + " from " + RedactSecretJsonInErrorMessageSanitizer.INSTANCE.sanitize(formData), e);
        }
    }

    /**
     * Replacement for {@link StaplerRequest2#bindJSON(Class, JSONObject)} which honors {@link #newInstance(StaplerRequest2, JSONObject)}.
     * This is automatically used inside {@link #newInstance(StaplerRequest2, JSONObject)} so a direct call would only be necessary
     * in case the top level binding might use a {@link Descriptor} which overrides {@link #newInstance(StaplerRequest2, JSONObject)}.
     * @since TODO
     */
    public static <T> T bindJSON(StaplerRequest2 req, Class<T> type, JSONObject src) {
        return bindJSON(req, type, src, false);
    }

    /**
     * @deprecated use {@link #bindJSON(StaplerRequest2, Class, JSONObject)}
     * @since 2.342
     */
    @Deprecated
    public static <T> T bindJSON(StaplerRequest req, Class<T> type, JSONObject src) {
        return bindJSON(StaplerRequest.toStaplerRequest2(req), type, src);
    }

    private static <T> T bindJSON(StaplerRequest2 req, Class<T> type, JSONObject src, boolean fromNewInstance) {
        BindInterceptor oldInterceptor = req.getBindInterceptor();
        try {
            NewInstanceBindInterceptor interceptor;
            if (oldInterceptor instanceof NewInstanceBindInterceptor) {
                interceptor = (NewInstanceBindInterceptor) oldInterceptor;
            } else {
                interceptor = new NewInstanceBindInterceptor(oldInterceptor);
                req.setBindInterceptor(interceptor);
            }
            if (fromNewInstance) {
                interceptor.processed.put(src, true);
            }
            return req.bindJSON(type, src);
        } finally {
            req.setBindInterceptor(oldInterceptor);
        }
    }

    /**
     * Ensures that calls to {@link StaplerRequest2#bindJSON(Class, JSONObject)} from {@link #newInstance(StaplerRequest2, JSONObject)} recurse properly.
     * {@code doConfigSubmit}-like methods will wind up calling {@code newInstance} directly
     * or via {@link #newInstancesFromHeteroList(StaplerRequest2, Object, Collection)},
     * which consult any custom {@code newInstance} overrides for top-level {@link Describable} objects.
     * But for nested describable objects Stapler would know nothing about {@code newInstance} without this trick.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static class NewInstanceBindInterceptor extends BindInterceptor {

        private final BindInterceptor oldInterceptor;
        private final IdentityHashMap<JSONObject, Boolean> processed = new IdentityHashMap<>();

        NewInstanceBindInterceptor(BindInterceptor oldInterceptor) {
            LOGGER.log(Level.FINER, "new interceptor delegating to {0}", oldInterceptor);
            this.oldInterceptor = oldInterceptor;
        }

        private boolean isApplicable(Class type, JSONObject json) {
            if (Modifier.isAbstract(type.getModifiers())) {
                LOGGER.log(Level.FINER, "ignoring abstract {0} {1}", new Object[] {type.getName(), json});
                return false;
            }
            if (!Describable.class.isAssignableFrom(type)) {
                LOGGER.log(Level.FINER, "ignoring non-Describable {0} {1}", new Object[] {type.getName(), json});
                return false;
            }
            if (Boolean.TRUE.equals(processed.put(json, true))) {
                LOGGER.log(Level.FINER, "already processed {0} {1}", new Object[] {type.getName(), json});
                return false;
            }
            return true;
        }

        @Override
        public Object instantiate(Class actualType, JSONObject json) {
            if (isApplicable(actualType, json)) {
                LOGGER.log(Level.FINE, "switching to newInstance {0} {1}", new Object[] {actualType.getName(), json});
                try {
                    final Descriptor descriptor = Jenkins.get().getDescriptor(actualType);
                    if (descriptor != null) {
                        return descriptor.newInstance(Stapler.getCurrentRequest2(), json);
                    } else {
                        LOGGER.log(Level.WARNING, "Descriptor not found. Falling back to default instantiation "
                                + actualType.getName() + " " + json);
                    }
                } catch (Exception x) {
                    LOGGER.log(x instanceof HttpResponse ? Level.FINE : Level.WARNING, "falling back to default instantiation " + actualType.getName() + " " + json, x);
                    // If nested objects are not using newInstance, bindJSON will wind up throwing the same exception anyway,
                    // so logging above will result in a duplicated stack trace.
                    // However if they *are* then this is the only way to find errors in that newInstance.
                    // Normally oldInterceptor.instantiate will just return DEFAULT, not actually do anything,
                    // so we cannot try calling the default instantiation and then decide which problem to report.
                }
            }
            return oldInterceptor.instantiate(actualType, json);
        }

        @Override
        public Object onConvert(Type targetType, Class targetTypeErasure, Object jsonSource) {
            if (jsonSource instanceof JSONObject json) {
                if (isApplicable(targetTypeErasure, json)) {
                    LOGGER.log(Level.FINE, "switching to newInstance {0} {1}", new Object[] {targetTypeErasure.getName(), json});
                    try {
                        return Jenkins.get().getDescriptor(targetTypeErasure).newInstance(Stapler.getCurrentRequest2(), json);
                    } catch (Exception x) {
                        LOGGER.log(Level.WARNING, "falling back to default instantiation " + targetTypeErasure.getName() + " " + json, x);
                    }
                }
            } else {
                LOGGER.log(Level.FINER, "ignoring non-object {0}", jsonSource);
            }
            return oldInterceptor.onConvert(targetType, targetTypeErasure, jsonSource);
        }

    }

    /**
     * Look out for a typical error a plugin developer makes.
     * See http://hudson.361315.n4.nabble.com/Help-Hint-needed-Post-build-action-doesn-t-stay-activated-td2308833.html
     */
    private T verifyNewInstance(T t) {
        if (t != null && t.getDescriptor() != this) {
            // TODO: should this be a fatal error?
            LOGGER.warning("Father of " + t + " and its getDescriptor() points to two different instances. Probably misplaced @Extension. See http://hudson.361315.n4.nabble.com/Help-Hint-needed-Post-build-action-doesn-t-stay-activated-td2308833.html");
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
     * the values are something like {@code "/plugin/emma/help.html"} to
     * refer to static resource files in a plugin, or {@code "/publisher/EmmaPublisher/abc"}
     * to refer to Jelly script {@code abc.jelly} or a method {@code EmmaPublisher.doAbc()}.
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
        return getHelpFile(getKlass(), fieldName);
    }

    @SuppressFBWarnings(value = "SBSC_USE_STRINGBUFFER_CONCATENATION", justification = "no big deal")
    public String getHelpFile(Klass<?> clazz, String fieldName) {
        HelpRedirect r = helpRedirect.get(fieldName);
        if (r != null)    return r.resolve();

        for (Klass<?> c : clazz.getAncestors()) {
            String page = "/descriptor/" + getId() + "/help";
            String suffix;
            if (fieldName == null) {
                suffix = "";
            } else {
                page += '/' + fieldName;
                suffix = '-' + fieldName;
            }

            try {
                if (Stapler.getCurrentRequest2().getView(c, "help" + suffix) != null)
                    return page;
            } catch (IOException e) {
                throw new Error(e);
            }

            if (getStaticHelpUrl(Stapler.getCurrentRequest2(), c, suffix) != null)    return page;
        }
        return null;
    }

    /**
     * Tells Jenkins that the help file for the field 'fieldName' is defined in the help file for
     * the 'fieldNameToRedirectTo' in the 'owner' class.
     * @since 1.425
     */
    protected void addHelpFileRedirect(String fieldName, Class<? extends Describable> owner, String fieldNameToRedirectTo) {
        helpRedirect.put(fieldName, new HelpRedirect(owner, fieldNameToRedirectTo));
    }

    /**
     * Checks if the given object is created from this {@link Descriptor}.
     */
    public final boolean isInstance(T instance) {
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
     *      As of 1.239, use {@link #configure(StaplerRequest2, JSONObject)}.
     */
    @Deprecated
    public boolean configure(StaplerRequest req) throws FormException {
        return true;
    }

    /**
     * Invoked when the global configuration page is submitted.
     *
     * Can be overridden to store descriptor-specific information.
     *
     * @param json
     *      The JSON object that captures the configuration data for this {@link Descriptor}.
     *      See <a href="https://www.jenkins.io/doc/developer/forms/structured-form-submission/">the developer documentation</a>.
     * @return false
     *      to keep the client in the same config page.
     * @since TODO
     */
    public boolean configure(StaplerRequest2 req, JSONObject json) throws FormException {
        if (Util.isOverridden(Descriptor.class, getClass(), "configure", StaplerRequest.class, JSONObject.class)) {
            return configure(StaplerRequest.fromStaplerRequest2(req), json);
        } else {
            // compatibility
            return configure(StaplerRequest.fromStaplerRequest2(req));
        }
    }

    /**
     * @deprecated use {@link #configure(StaplerRequest2, JSONObject)}
     */
    @Deprecated
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        // compatibility
        return configure(req);
    }

    public String getConfigPage() {
        return getViewPage(clazz, getPossibleViewNames("config"), "config.jelly");
    }

    public String getGlobalConfigPage() {
        return getViewPage(clazz, getPossibleViewNames("global"), null);
    }

    /**
     * Define the global configuration category the global config of this Descriptor is in.
     *
     * @return never null, always the same value for a given instance of {@link Descriptor}.
     *
     * @since 2.0, used to be in {@link GlobalConfiguration} before that.
     */
    public @NonNull GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Unclassified.class);
    }

    /**
     * Returns the permission type needed in order to access the {@link #getGlobalConfigPage()} for this descriptor.
     * By default, requires {@link Jenkins#ADMINISTER} permission.
     * For now this only applies to descriptors configured through the global ({@link jenkins.model.GlobalConfigurationCategory.Unclassified}) configuration.
     * Override to return something different if appropriate. The only currently supported alternative return value is {@link Jenkins#MANAGE}.
     *
     * @return Permission required to globally configure this descriptor.
     * @since 2.222
     */
    public @NonNull
    Permission getRequiredGlobalConfigPagePermission() {
        return Jenkins.ADMINISTER;
    }

    private String getViewPage(Class<?> clazz, String pageName, String defaultValue) {
        return getViewPage(clazz, Set.of(pageName), defaultValue);
    }

    private String getViewPage(Class<?> clazz, Collection<String> pageNames, String defaultValue) {
        while (clazz != Object.class && clazz != null) {
            for (String pageName : pageNames) {
                String name = clazz.getName().replace('.', '/').replace('$', '/') + "/" + pageName;
                if (clazz.getClassLoader().getResource(name) != null)
                    return '/' + name;
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
        return getViewPage(clazz, pageName, pageName);
    }

    protected List<String> getPossibleViewNames(String baseName) {
        List<String> names = new ArrayList<>();
        for (Facet f : WebApp.get(Jenkins.get().getServletContext()).facets) {
            if (f instanceof JellyCompatibleFacet jcf) {
                for (String ext : jcf.getScriptExtensions())
                    names.add(baseName + ext);
            }
        }
        return names;
    }


    /**
     * Saves the configuration info to the disk.
     */
    @Override
    public synchronized void save() {
        if (BulkChange.contains(this))   return;
        try {
            getConfigFile().write(this);
            SaveableListener.fireOnChange(this, getConfigFile());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save " + getConfigFile(), e);
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
    @Override
    public synchronized void load() {
        XmlFile file = getConfigFile();
        if (!file.exists())
            return;

        try {
            file.unmarshal(this);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to load " + file, e);
        }
    }

    protected XmlFile getConfigFile() {
        return new XmlFile(new File(Jenkins.get().getRootDir(), getId() + ".xml"));
    }

    /**
     * Returns the plugin in which this descriptor is defined.
     *
     * @return
     *      null to indicate that this descriptor came from the core.
     */
    protected PluginWrapper getPlugin() {
        return Jenkins.get().getPluginManager().whichPlugin(clazz);
    }

    /**
     * Serves {@code help.html} from the resource of {@link #clazz}.
     */
    public void doHelp(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        if (Util.isOverridden(Descriptor.class, getClass(), "doHelp", StaplerRequest.class, StaplerResponse.class)) {
            try {
                doHelp(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        } else {
            doHelpImpl(req, rsp);
        }
    }

    /**
     * @deprecated use {@link #doHelp(StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    @StaplerNotDispatchable
    public void doHelp(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            doHelpImpl(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    private void doHelpImpl(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        String path = req.getRestOfPath();
        if (path.contains("..")) throw new ServletException("Illegal path: " + path);

        path = path.replace('/', '-');

        PluginWrapper pw = getPlugin();
        if (pw != null) {
            rsp.setHeader("X-Plugin-Short-Name", pw.getShortName());
            rsp.setHeader("X-Plugin-Long-Name", pw.getLongName());
            rsp.setHeader("X-Plugin-From", Messages.Descriptor_From(
                    pw.getLongName().replace("Hudson", "Jenkins").replace("hudson", "jenkins"), pw.getUrl()));
        }

        for (Klass<?> c = getKlass(); c != null; c = c.getSuperClass()) {
            RequestDispatcher rd = Stapler.getCurrentRequest2().getView(c, "help" + path);
            if (rd != null) { // template based help page
                rd.forward(req, rsp);
                return;
            }

            URL url = getStaticHelpUrl(Stapler.getCurrentRequest2(), c, path);
            if (url != null) {
                // TODO: generalize macro expansion and perhaps even support JEXL
                rsp.setContentType("text/html;charset=UTF-8");
                try (InputStream in = url.openStream()) {
                    String literal = IOUtils.toString(in, StandardCharsets.UTF_8);
                    rsp.getWriter().println(Util.replaceMacro(literal, Map.of("rootURL", req.getContextPath())));
                }
                return;
            }
        }
        rsp.sendError(SC_NOT_FOUND);
    }

    /**
     * @since TODO
     */
    @Restricted(NoExternalUse.class)
    public static URL getStaticHelpUrl(StaplerRequest2 req, Klass<?> c, String suffix) {

        String base = "help" + suffix;
        URL url;

        Enumeration<Locale> locales = req.getLocales();
        while (locales.hasMoreElements()) {
            Locale locale = locales.nextElement();
            url = c.getResource(base + '_' + locale.getLanguage() + '_' + locale.getCountry() + '_' + locale.getVariant() + ".html");
            if (url != null)    return url;
            url = c.getResource(base + '_' + locale.getLanguage() + '_' + locale.getCountry() + ".html");
            if (url != null)    return url;
            url = c.getResource(base + '_' + locale.getLanguage() + ".html");
            if (url != null)    return url;
            if (locale.getLanguage().equals("en")) {
                url = c.getResource(base + ".html");
                if (url != null)    return url;
            }
        }

        // default
        return c.getResource(base + ".html");
    }

    /**
     * @deprecated use {@link #getStaticHelpUrl(StaplerRequest2, Klass, String)}
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    public static URL getStaticHelpUrl(StaplerRequest req, Klass<?> c, String suffix) {
        return getStaticHelpUrl(StaplerRequest.toStaplerRequest2(req), c, suffix);
    }


//
// static methods
//


    // to work around warning when creating a generic array type
    public static <T> T[] toArray(T... values) {
        return values;
    }

    public static <T> List<T> toList(T... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    public static <T extends Describable<T>>
    Map<Descriptor<T>, T> toMap(Iterable<T> describables) {
        Map<Descriptor<T>, T> m = new LinkedHashMap<>();
        for (T d : describables) {
            Descriptor<T> descriptor;
            try {
                descriptor = d.getDescriptor();
            } catch (Throwable x) {
                LOGGER.log(Level.WARNING, null, x);
                continue;
            }
            m.put(descriptor, d);
        }
        return m;
    }

    /**
     * Used to build {@link Describable} instance list from {@code <f:hetero-list>} tag.
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
     * @since TODO
     */
    public static <T extends Describable<T>>
    List<T> newInstancesFromHeteroList(StaplerRequest2 req, JSONObject formData, String key,
                Collection<? extends Descriptor<T>> descriptors) throws FormException {

        return newInstancesFromHeteroList(req, formData.get(key), descriptors);
    }

    /**
     * @deprecated use {@link #newInstancesFromHeteroList(StaplerRequest2, JSONObject, String, Collection)}
     */
    @Deprecated
    public static <T extends Describable<T>>
    List<T> newInstancesFromHeteroList(StaplerRequest req, JSONObject formData, String key,
                                       Collection<? extends Descriptor<T>> descriptors) throws FormException {
        return newInstancesFromHeteroList(StaplerRequest.toStaplerRequest2(req), formData, key, descriptors);
    }

    /**
     * @since TODO
     */
    public static <T extends Describable<T>>
    List<T> newInstancesFromHeteroList(StaplerRequest2 req, Object formData,
                Collection<? extends Descriptor<T>> descriptors) throws FormException {

        List<T> items = new ArrayList<>();

        if (formData != null) {
            for (Object o : JSONArray.fromObject(formData)) {
                JSONObject jo = (JSONObject) o;
                Descriptor<T> d = null;
                // 'kind' and '$class' are mutually exclusive (see class-entry.jelly), but to be more lenient on the reader side,
                // we check them both anyway. 'kind' (which maps to ID) is more unique than '$class', which can have multiple matching
                // Descriptors, so we prefer 'kind' if it's present.
                String kind = jo.optString("kind", null);
                if (kind != null) {
                    // Only applies when Descriptor.getId is overridden.
                    // Note that kind is only supported here,
                    // *not* inside the StaplerRequest2.bindJSON which is normally called by newInstance
                    // (since Descriptor.newInstance is not itself available to Stapler).
                    // If you merely override getId for some reason, but use @DataBoundConstructor on your Describable,
                    // there is no problem; but you can only rely on newInstance being called at top level.
                    d = findById(descriptors, kind);
                }
                if (d == null) {
                  kind = jo.optString("$class");
                  if (kind != null) { // else we will fall through to the warning
                      // This is the normal case.
                      d = findByDescribableClassName(descriptors, kind);
                      if (d == null) {
                          // Deprecated system where stapler-class was the Descriptor class name (rather than Describable class name).
                          d = findByClassName(descriptors, kind);
                      }
                  }
                }
                if (d != null) {
                    items.add(d.newInstance(req, jo));
                } else {
                    LOGGER.log(Level.WARNING, "Received unexpected form data element: {0}", jo);
                }
            }
        }

        return items;
    }

    /**
     * @deprecated use {@link #newInstancesFromHeteroList(StaplerRequest2, JSONObject, String, Collection)}
     */
    @Deprecated
    public static <T extends Describable<T>>
    List<T> newInstancesFromHeteroList(StaplerRequest req, Object formData,
                                       Collection<? extends Descriptor<T>> descriptors) throws FormException {
        return newInstancesFromHeteroList(StaplerRequest.toStaplerRequest2(req), formData, descriptors);
    }

    /**
     * Finds a descriptor from a collection by its ID.
     * @param id should match {@link #getId}
     * @since 1.610
     */
    public static @CheckForNull <T extends Descriptor> T findById(Collection<? extends T> list, String id) {
        for (T d : list) {
            if (d.getId().equals(id))
                return d;
        }
        return null;
    }

    /**
     * Finds a descriptor from a collection by the class name of the {@link Descriptor}.
     * This is useless as of the introduction of {@link #getId} and so only very old compatibility code needs it.
     */
    private static @CheckForNull <T extends Descriptor> T findByClassName(Collection<? extends T> list, String className) {
        for (T d : list) {
            if (d.getClass().getName().equals(className))
                return d;
        }
        return null;
    }

    /**
     * Finds a descriptor from a collection by the class name of the {@link Describable} it describes.
     * @param className should match {@link Class#getName} of a {@link #clazz}
     * @since 1.610
     */
    public static @CheckForNull <T extends Descriptor> T findByDescribableClassName(Collection<? extends T> list, String className) {
        for (T d : list) {
            if (d.clazz.getName().equals(className))
                return d;
        }
        return null;
    }

    /**
     * Finds a descriptor from a collection by its class name or ID.
     * @deprecated choose between {@link #findById} or {@link #findByDescribableClassName}
     */
    @Deprecated
    public static @CheckForNull <T extends Descriptor> T find(Collection<? extends T> list, String string) {
        T d = findByClassName(list, string);
        if (d != null) {
                return d;
        }
        return findById(list, string);
    }

    /**
     * @deprecated choose between {@link #findById} or {@link #findByDescribableClassName}
     */
    @Deprecated
    public static @CheckForNull Descriptor find(String className) {
        return find(ExtensionList.lookup(Descriptor.class), className);
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

        @Override
        public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object node) throws IOException, ServletException {
            if (FormApply.isApply(req)) {
                FormApply.applyResponse("notificationBar.show(" + quote(getMessage()) + ",notificationBar.ERROR)")
                        .generateResponse(req, rsp, node);
            } else {
                // for now, we can't really use the field name that caused the problem.
                new Failure(getMessage()).generateResponse(req, rsp, node, getCause());
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
