/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Alan Harder
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

package hudson.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.ConverterLookup;
import com.thoughtworks.xstream.converters.ConverterMatcher;
import com.thoughtworks.xstream.converters.ConverterRegistry;
import com.thoughtworks.xstream.converters.DataHolder;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.SingleValueConverter;
import com.thoughtworks.xstream.converters.SingleValueConverterWrapper;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.extended.DynamicProxyConverter;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.core.ClassLoaderReference;
import com.thoughtworks.xstream.core.JVM;
import com.thoughtworks.xstream.core.util.Fields;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.ReaderWrapper;
import com.thoughtworks.xstream.io.xml.StandardStaxDriver;
import com.thoughtworks.xstream.mapper.CannotResolveClassException;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.mapper.MapperWrapper;
import com.thoughtworks.xstream.security.AnyTypePermission;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.XmlFile;
import hudson.diagnosis.OldDataMonitor;
import hudson.model.Label;
import hudson.model.Result;
import hudson.model.Saveable;
import hudson.remoting.ClassFilter;
import hudson.util.xstream.ImmutableListConverter;
import hudson.util.xstream.ImmutableMapConverter;
import hudson.util.xstream.ImmutableSetConverter;
import hudson.util.xstream.ImmutableSortedSetConverter;
import hudson.util.xstream.MapperDelegate;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import jenkins.util.xstream.SafeURLConverter;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * {@link XStream} customized in various ways for Jenkins’ needs.
 * Most importantly, integrates {@link RobustReflectionConverter}.
 */
public class XStream2 extends XStream {

    private static final Logger LOGGER = Logger.getLogger(XStream2.class.getName());
    /**
     * Determine what is the value (in seconds) of the "collectionUpdateLimit" added by XStream
     * to protect against <a href="http://x-stream.github.io/CVE-2021-43859.html">CVE-2021-43859</a>.
     * It corresponds to the accumulated timeout when adding an item to a collection.
     *
     * Default: 5 seconds (in contrary to XStream default to 20 which is a bit too tolerant)
     * If negative: disable the DoS protection
     */
    @Restricted(NoExternalUse.class)
    public static final String COLLECTION_UPDATE_LIMIT_PROPERTY_NAME = XStream2.class.getName() + ".collectionUpdateLimit";
    private static final int COLLECTION_UPDATE_LIMIT_DEFAULT_VALUE = 5;

    private RobustReflectionConverter reflectionConverter;
    private final ThreadLocal<Boolean> oldData = new ThreadLocal<>();
    private final @CheckForNull ClassOwnership classOwnership;
    private final Map<String, Class<?>> compatibilityAliases = new ConcurrentHashMap<>();

    /**
     * Hook to insert {@link Mapper}s after they are created.
     */
    private MapperInjectionPoint mapperInjectionPoint;

    /**
     * Convenience method so we only have to change the driver in one place
     * if we switch to something new in the future
     *
     * @return a new instance of the HierarchicalStreamDriver we want to use
     */
    public static HierarchicalStreamDriver getDefaultDriver() {
        return new StaxDriver();
    }

    private static class StaxDriver extends StandardStaxDriver {
        /*
         * The below two methods are copied from com.thoughtworks.xstream.io.xml.AbstractXppDriver to preserve
         * compatibility.
         */

        @Override
        public HierarchicalStreamWriter createWriter(Writer out) {
            return new PrettyPrintWriter(out, PrettyPrintWriter.XML_1_1, getNameCoder());
        }

        @Override
        public HierarchicalStreamWriter createWriter(OutputStream out) {
            /*
             * While it is tempting to use StandardCharsets.UTF_8 here, this would break
             * hudson.util.XStream2EncodingTest#toXMLUnspecifiedEncoding.
             */
            return createWriter(new OutputStreamWriter(out, Charset.defaultCharset()));
        }

        /*
         * The below two methods are copied from com.thoughtworks.xstream.io.xml.StaxDriver for Java 17 compatibility.
         */

        @Override
        protected XMLInputFactory createInputFactory() {
            final XMLInputFactory instance = XMLInputFactory.newInstance();
            instance.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
            instance.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            return instance;
        }

        @Override
        protected XMLOutputFactory createOutputFactory() {
            return XMLOutputFactory.newInstance();
        }
    }

    public XStream2() {
        super(getDefaultDriver());
        init();
        classOwnership = null;
    }

    public XStream2(HierarchicalStreamDriver hierarchicalStreamDriver) {
        super(hierarchicalStreamDriver);
        init();
        classOwnership = null;
    }

    /**
     * @since 2.318
     */
    public XStream2(ReflectionProvider reflectionProvider, HierarchicalStreamDriver driver,
                    ClassLoaderReference classLoaderReference, Mapper mapper, ConverterLookup converterLookup,
                    ConverterRegistry converterRegistry) {
        super(reflectionProvider, driver, classLoaderReference, mapper, converterLookup, converterRegistry);
        init();
        classOwnership = null;
    }

    XStream2(ClassOwnership classOwnership) {
        super(getDefaultDriver());
        init();
        this.classOwnership = classOwnership;
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, Object root, DataHolder dataHolder) {
        return unmarshal(reader, root, dataHolder, false);
    }

    /**
     * Variant of {@link #unmarshal(HierarchicalStreamReader, Object, DataHolder)} that nulls out non-{@code transient} instance fields not defined in the source when unmarshaling into an existing object.
     * <p>Typically useful when loading user-supplied XML files in place (non-null {@code root})
     * where some reference-valued fields of the root object may have legitimate reasons for being null.
     * Without this mode, it is impossible to clear such fields in an existing instance,
     * since XStream has no notation for a null field value.
     * Even for primitive-valued fields, it is useful to guarantee
     * that unmarshaling will produce the same result as creating a new instance.
     * <p>Do <em>not</em> use in cases where the root objects defines fields (typically {@code final})
     * which it expects to be {@link NonNull} unless you are prepared to restore default values for those fields.
     * @param nullOut whether to perform this special behavior;
     *                false to use the stock XStream behavior of leaving unmentioned {@code root} fields untouched
     * @see XmlFile#unmarshalNullingOut
     * @see <a href="https://issues.jenkins.io/browse/JENKINS-21017">JENKINS-21017</a>
     * @since 2.99
     */
    public Object unmarshal(HierarchicalStreamReader reader, Object root, DataHolder dataHolder, boolean nullOut) {
        // init() is too early to do this
        // defensive because some use of XStream happens before plugins are initialized.
        Jenkins h = Jenkins.getInstanceOrNull();
        if (h != null && h.pluginManager != null && h.pluginManager.uberClassLoader != null) {
            setClassLoader(h.pluginManager.uberClassLoader);
        }

        Object o;
        if (root == null || !nullOut) {
            o = super.unmarshal(reader, root, dataHolder);
        } else {
            Set<String> topLevelFields = new HashSet<>();
            o = super.unmarshal(new ReaderWrapper(reader) {
                int depth;
                @Override
                public void moveUp() {
                    if (--depth == 0) {
                        topLevelFields.add(getNodeName());
                    }
                    super.moveUp();
                }

                @Override
                public void moveDown() {
                    try {
                        super.moveDown();
                    } finally {
                        depth++;
                    }
                }
            }, root, dataHolder);
            if (o == root && getConverterLookup().lookupConverterForType(o.getClass()) instanceof RobustReflectionConverter) {
                getReflectionProvider().visitSerializableFields(o, (String name, Class type, Class definedIn, Object value) -> {
                    if (topLevelFields.contains(name)) {
                        return;
                    }
                    Field f = Fields.find(definedIn, name);
                    Object v;
                    if (type.isPrimitive()) {
                        // oddly not in com.thoughtworks.xstream.core.util.Primitives
                        v = ReflectionUtils.getVmDefaultValueForPrimitiveType(type);
                        if (v.equals(value)) {
                            return;
                        }
                    } else {
                        if (value == null) {
                            return;
                        }
                        v = null;
                    }
                    LOGGER.log(Level.FINE, "JENKINS-21017: nulling out {0} in {1}", new Object[] {f, o});
                    Fields.write(f, o, v);
                });
            }
        }

        if (oldData.get() != null) {
            oldData.remove();
            if (o instanceof Saveable) OldDataMonitor.report((Saveable) o, "1.106");
        }
        return o;
    }

    @Override
    protected void setupConverters() {
        super.setupConverters();
        // replace default reflection converter
        reflectionConverter = new RobustReflectionConverter(getMapper(), JVM.newReflectionProvider(), new PluginClassOwnership());
        registerConverter(reflectionConverter, PRIORITY_VERY_LOW + 1);
    }

    /**
     * Specifies that a given field of a given class should not be treated with laxity by {@link RobustCollectionConverter}.
     * @param clazz a class which we expect to hold a non-{@code transient} field
     * @param field a field name in that class
     * @since 2.85 this method can be used from outside core, before then it was restricted since initially added in 1.551 / 1.532.2
     */
    public void addCriticalField(Class<?> clazz, String field) {
        reflectionConverter.addCriticalField(clazz, field);
    }

    static String trimVersion(String version) {
        // TODO seems like there should be some trick with VersionNumber to do this
        return version.replaceFirst(" .+$", "");
    }

    private void init() {
        int updateLimit = SystemProperties.getInteger(COLLECTION_UPDATE_LIMIT_PROPERTY_NAME, COLLECTION_UPDATE_LIMIT_DEFAULT_VALUE);
        this.setCollectionUpdateLimit(updateLimit);

        // list up types that should be marshalled out like a value, without referential integrity tracking.
        addImmutableType(Result.class, false);

        // http://www.openwall.com/lists/oss-security/2017/04/03/4
        denyTypes(new Class[] { void.class, Void.class });

        registerConverter(new RobustCollectionConverter(getMapper(), getReflectionProvider()), 10);
        registerConverter(new RobustMapConverter(getMapper()), 10);
        registerConverter(new ImmutableMapConverter(getMapper(), getReflectionProvider()), 10);
        registerConverter(new ImmutableSortedSetConverter(getMapper(), getReflectionProvider()), 10);
        registerConverter(new ImmutableSetConverter(getMapper(), getReflectionProvider()), 10);
        registerConverter(new ImmutableListConverter(getMapper(), getReflectionProvider()), 10);
        registerConverter(new CopyOnWriteMap.Tree.ConverterImpl(getMapper()), 10); // needs to override MapConverter
        registerConverter(new DescribableList.ConverterImpl(getMapper()), 10); // explicitly added to handle subtypes
        registerConverter(new Label.ConverterImpl(), 10);
        // SECURITY-637 against URL deserialization
        registerConverter(new SafeURLConverter(), 10);

        // this should come after all the XStream's default simpler converters,
        // but before reflection-based one kicks in.
        registerConverter(new AssociatedConverterImpl(this), -10);

        registerConverter(new BlacklistedTypesConverter(), PRIORITY_VERY_HIGH); // SECURITY-247 defense
        addPermission(AnyTypePermission.ANY); // covered by JEP-200, avoid securityWarningGiven

        registerConverter(new DynamicProxyConverter(getMapper(), new ClassLoaderReference(getClassLoader())) { // SECURITY-105 defense
            @Override public boolean canConvert(Class type) {
                return /* this precedes NullConverter */ type != null && super.canConvert(type);
            }

            @Override public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
                throw new ConversionException("<dynamic-proxy> not supported");
            }
        }, PRIORITY_VERY_HIGH);
    }

    @Override
    protected MapperWrapper wrapMapper(MapperWrapper next) {
        Mapper m = new CompatibilityMapper(new MapperWrapper(next) {
            @Override
            public String serializedClass(Class type) {
                if (type != null && ImmutableMap.class.isAssignableFrom(type))
                    return super.serializedClass(ImmutableMap.class);
                else if (type != null && ImmutableList.class.isAssignableFrom(type))
                    return super.serializedClass(ImmutableList.class);
                else
                    return super.serializedClass(type);
            }
        });

        mapperInjectionPoint = new MapperInjectionPoint(m);

        return mapperInjectionPoint;
    }

    public Mapper getMapperInjectionPoint() {
        return mapperInjectionPoint.getDelegate();
    }

    /**
     * @deprecated Uses default encoding yet fails to write an encoding header. Prefer {@link #toXMLUTF8}.
     */
    @Deprecated
    @Override public void toXML(Object obj, OutputStream out) {
        super.toXML(obj, out);
    }

    /**
     * Serializes to a byte stream.
     * Uses UTF-8 encoding and specifies that in the XML encoding declaration.
     * @since 1.504
     */
    public void toXMLUTF8(Object obj, OutputStream out) throws IOException {
        Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        w.write("<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n");
        toXML(obj, w);
    }

    /**
     * This method allows one to insert additional mappers after {@link XStream2} was created,
     * but because of the way XStream works internally, this needs to be done carefully.
     * Namely,
     *
     * <ol>
     * <li>You need to {@link #getMapperInjectionPoint()} wrap it, then put that back into {@link #setMapper(Mapper)}.
     * <li>The whole sequence needs to be synchronized against this object to avoid a concurrency issue.
     * </ol>
     */
    public void setMapper(Mapper m) {
        mapperInjectionPoint.setDelegate(m);
    }

    static final class MapperInjectionPoint extends MapperDelegate {
        MapperInjectionPoint(Mapper wrapped) {
            super(wrapped);
        }

        public Mapper getDelegate() {
            return delegate;
        }

        public void setDelegate(Mapper m) {
            delegate = m;
        }
    }

    /**
     * Adds an alias in case class names change.
     *
     * Unlike {@link #alias(String, Class)}, which uses the registered alias name for writing XML,
     * this method registers an alias to be used only for the sake of reading from XML. This makes
     * this method usable for the situation when class names change.
     *
     * @param oldClassName
     *      Fully qualified name of the old class name.
     * @param newClass
     *      New class that's field-compatible with the given old class name.
     * @since 1.416
     */
    public void addCompatibilityAlias(String oldClassName, Class newClass) {
        compatibilityAliases.put(oldClassName, newClass);
    }

    /**
     * Prior to Hudson 1.106, XStream 1.1.x was used which encoded "$" in class names
     * as "-" instead of "_-" that is used now.  Up through Hudson 1.348 compatibility
     * for old serialized data was maintained via {@link com.thoughtworks.xstream.mapper.XStream11XmlFriendlyMapper}.
     * However, it was found (JENKINS-5768) that this caused fields with "__" to fail
     * deserialization due to double decoding.  Now this class is used for compatibility.
     */
    private class CompatibilityMapper extends MapperWrapper {
        private CompatibilityMapper(Mapper wrapped) {
            super(wrapped);
        }

        @Override
        public Class realClass(String elementName) {
            Class s = compatibilityAliases.get(elementName);
            if (s != null)    return s;

            try {
                return super.realClass(elementName);
            } catch (CannotResolveClassException e) {
                // If a "-" is found, retry with mapping this to "$"
                if (elementName.indexOf('-') >= 0) try {
                    Class c = super.realClass(elementName.replace('-', '$'));
                    oldData.set(Boolean.TRUE);
                    return c;
                } catch (CannotResolveClassException e2) { }
                // Throw original exception
                throw e;
            }
        }
    }

    /**
     * If a class defines a nested {@code ConverterImpl} subclass, use that as a {@link Converter}.
     * Its constructor may have XStream/XStream2 and/or Mapper parameters (or no params).
     */
    private static final class AssociatedConverterImpl implements Converter {
        private final XStream xstream;
        private static final ClassValue<Class<? extends ConverterMatcher>> classCache = new ClassValue<>() {
            @Override
            protected Class<? extends ConverterMatcher> computeValue(Class<?> type) {
                return computeConverterClass(type);
            }
        };
        private final ConcurrentHashMap<Class<?>, Converter> cache =
                new ConcurrentHashMap<>();

        private AssociatedConverterImpl(XStream xstream) {
            this.xstream = xstream;
        }

        @CheckForNull
        private Converter findConverter(@CheckForNull Class<?> t) {
            if (t == null) {
                return null;
            }
            Converter result = cache.computeIfAbsent(t, unused -> computeConverter(t));
            // ConcurrentHashMap does not allow null, so use this object to represent null
            return result == this ? null : result;
        }

        @CheckForNull
        private static Class<? extends ConverterMatcher> computeConverterClass(@NonNull Class<?> t) {
            try {
                final ClassLoader classLoader = t.getClassLoader();
                if (classLoader == null) {
                    return null;
                }
                String name = t.getName() + "$ConverterImpl";
                if (classLoader.getResource(name.replace('.', '/') + ".class") == null) {
                    return null;
                }
                return classLoader.loadClass(name).asSubclass(ConverterMatcher.class);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }

        @CheckForNull
        private Converter computeConverter(@NonNull Class<?> t) {
            Class<? extends ConverterMatcher> cl = classCache.get(t);
            if (cl == null) {
                // See above.. this object in cache represents null
                return this;
            }
            try {
                Constructor<?> c = cl.getConstructors()[0];

                Class<?>[] p = c.getParameterTypes();
                Object[] args = new Object[p.length];
                for (int i = 0; i < p.length; i++) {
                    if (p[i] == XStream.class || p[i] == XStream2.class)
                        args[i] = xstream;
                    else if (p[i] == Mapper.class)
                        args[i] = xstream.getMapper();
                    else
                        throw new InstantiationError("Unrecognized constructor parameter: " + p[i]);

                }
                ConverterMatcher cm = (ConverterMatcher) c.newInstance(args);
                return cm instanceof SingleValueConverter
                        ? new SingleValueConverterWrapper((SingleValueConverter) cm)
                        : (Converter) cm;
            } catch (IllegalAccessException e) {
                IllegalAccessError x = new IllegalAccessError();
                x.initCause(e);
                throw x;
            } catch (InstantiationException | InvocationTargetException e) {
                InstantiationError x = new InstantiationError();
                x.initCause(e);
                throw x;
            }
        }

        @Override
        public boolean canConvert(Class type) {
            return findConverter(type) != null;
        }

        @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "TODO needs triage")
        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            findConverter(source.getClass()).marshal(source, writer, context);
        }

        @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "TODO needs triage")
        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            return findConverter(context.getRequiredType()).unmarshal(reader, context);
        }
    }

    /**
     * Create a nested {@code ConverterImpl} subclass that extends this class to run some
     * callback code just after a type is unmarshalled by RobustReflectionConverter.
     * Example: <pre> public static class ConverterImpl extends XStream2.PassthruConverter&lt;MyType&gt; {
     *   public ConverterImpl(XStream2 xstream) { super(xstream); }
     *   {@literal @}Override protected void callback(MyType obj, UnmarshallingContext context) {
     *     ...
     * </pre>
     */
    public abstract static class PassthruConverter<T> implements Converter {
        private Converter converter;

        protected PassthruConverter(XStream2 xstream) {
            converter = xstream.reflectionConverter;
        }

        @Override
        public boolean canConvert(Class type) {
            // marshal/unmarshal called directly from AssociatedConverterImpl
            return false;
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            converter.marshal(source, writer, context);
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            Object obj = converter.unmarshal(reader, context);
            callback((T) obj, context);
            return obj;
        }

        protected abstract void callback(T obj, UnmarshallingContext context);
    }

    /**
     * Marks serialized classes as being owned by particular components.
     */
    interface ClassOwnership {
        /**
         * Looks up the owner of a class, if any.
         * @param clazz a class which might be from a plugin
         * @return an identifier such as plugin name, or null
         */
        @CheckForNull String ownerOf(Class<?> clazz);
    }

    class PluginClassOwnership implements ClassOwnership {

        private PluginManager pm;

        @Override public String ownerOf(Class<?> clazz) {
            if (classOwnership != null) {
                return classOwnership.ownerOf(clazz);
            }
            if (pm == null) {
                Jenkins j = Jenkins.getInstanceOrNull();
                if (j != null) {
                    pm = j.getPluginManager();
                }
            }
            if (pm == null) {
                return null;
            }
            // TODO: possibly recursively scan super class to discover dependencies
            PluginWrapper p = pm.whichPlugin(clazz);
            return p != null ? p.getShortName() + '@' + trimVersion(p.getVersion()) : null;
        }

    }

    private static class BlacklistedTypesConverter implements Converter {
        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            throw new UnsupportedOperationException("Refusing to marshal " + source.getClass().getName() + " for security reasons; see https://www.jenkins.io/redirect/class-filter/");
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            throw new ConversionException("Refusing to unmarshal " + reader.getNodeName() + " for security reasons; see https://www.jenkins.io/redirect/class-filter/");
        }

        @Override
        public boolean canConvert(Class type) {
            if (type == null) {
                return false;
            }
            String name = type.getName();
            // claim we can convert all the scary stuff so we can throw exceptions when attempting to do so
            return ClassFilter.DEFAULT.isBlacklisted(name) || ClassFilter.DEFAULT.isBlacklisted(type);
        }
    }
}
