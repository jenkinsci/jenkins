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
import com.thoughtworks.xstream.mapper.AnnotationMapper;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.mapper.MapperWrapper;
import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.ConverterMatcher;
import com.thoughtworks.xstream.converters.DataHolder;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.SingleValueConverter;
import com.thoughtworks.xstream.converters.SingleValueConverterWrapper;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.extended.DynamicProxyConverter;
import com.thoughtworks.xstream.core.JVM;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.CannotResolveClassException;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.diagnosis.OldDataMonitor;
import hudson.remoting.ClassFilter;
import hudson.util.xstream.ImmutableSetConverter;
import hudson.util.xstream.ImmutableSortedSetConverter;
import jenkins.model.Jenkins;
import hudson.model.Label;
import hudson.model.Result;
import hudson.model.Saveable;
import hudson.util.xstream.ImmutableListConverter;
import hudson.util.xstream.ImmutableMapConverter;
import hudson.util.xstream.MapperDelegate;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.CheckForNull;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * {@link XStream} enhanced for additional Java5 support and improved robustness.
 * @author Kohsuke Kawaguchi
 */
public class XStream2 extends XStream {
    private RobustReflectionConverter reflectionConverter;
    private final ThreadLocal<Boolean> oldData = new ThreadLocal<Boolean>();
    private final @CheckForNull ClassOwnership classOwnership;
    private final Map<String,Class<?>> compatibilityAliases = new ConcurrentHashMap<String, Class<?>>();

    /**
     * Hook to insert {@link Mapper}s after they are created.
     */
    private MapperInjectionPoint mapperInjectionPoint;

    public XStream2() {
        init();
        classOwnership = null;
    }

    public XStream2(HierarchicalStreamDriver hierarchicalStreamDriver) {
        super(hierarchicalStreamDriver);
        init();
        classOwnership = null;
    }

    XStream2(ClassOwnership classOwnership) {
        init();
        this.classOwnership = classOwnership;
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, Object root, DataHolder dataHolder) {
        // init() is too early to do this
        // defensive because some use of XStream happens before plugins are initialized.
        Jenkins h = Jenkins.getInstance();
        if(h!=null && h.pluginManager!=null && h.pluginManager.uberClassLoader!=null) {
            setClassLoader(h.pluginManager.uberClassLoader);
        }

        Object o = super.unmarshal(reader,root,dataHolder);
        if (oldData.get()!=null) {
            oldData.remove();
            if (o instanceof Saveable) OldDataMonitor.report((Saveable)o, "1.106");
        }
        return o;
    }

    @Override
    protected Converter createDefaultConverter() {
        // replace default reflection converter
        reflectionConverter = new RobustReflectionConverter(getMapper(),new JVM().bestReflectionProvider(), new PluginClassOwnership());
        return reflectionConverter;
    }

    /**
     * Specifies that a given field of a given class should not be treated with laxity by {@link RobustCollectionConverter}.
     * @param clazz a class which we expect to hold a non-{@code transient} field
     * @param field a field name in that class
     */
    @Restricted(NoExternalUse.class) // TODO could be opened up later
    public void addCriticalField(Class<?> clazz, String field) {
        reflectionConverter.addCriticalField(clazz, field);
    }

    static String trimVersion(String version) {
        // TODO seems like there should be some trick with VersionNumber to do this
        return version.replaceFirst(" .+$", "");
    }

    private void init() {
        // list up types that should be marshalled out like a value, without referential integrity tracking.
        addImmutableType(Result.class);

        registerConverter(new RobustCollectionConverter(getMapper(),getReflectionProvider()),10);
        registerConverter(new RobustMapConverter(getMapper()), 10);
        registerConverter(new ImmutableMapConverter(getMapper(),getReflectionProvider()),10);
        registerConverter(new ImmutableSortedSetConverter(getMapper(),getReflectionProvider()),10);
        registerConverter(new ImmutableSetConverter(getMapper(),getReflectionProvider()),10);
        registerConverter(new ImmutableListConverter(getMapper(),getReflectionProvider()),10);
        registerConverter(new ConcurrentHashMapConverter(getMapper(),getReflectionProvider()),10);
        registerConverter(new CopyOnWriteMap.Tree.ConverterImpl(getMapper()),10); // needs to override MapConverter
        registerConverter(new DescribableList.ConverterImpl(getMapper()),10); // explicitly added to handle subtypes
        registerConverter(new Label.ConverterImpl(),10);

        // this should come after all the XStream's default simpler converters,
        // but before reflection-based one kicks in.
        registerConverter(new AssociatedConverterImpl(this), -10);

        registerConverter(new BlacklistedTypesConverter(), PRIORITY_VERY_HIGH); // SECURITY-247 defense

        registerConverter(new DynamicProxyConverter(getMapper()) { // SECURITY-105 defense
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
        AnnotationMapper a = new AnnotationMapper(m, getConverterRegistry(), getConverterLookup(), getClassLoader(), getReflectionProvider(), getJvm());
        // TODO JENKINS-19561 this is unsafe:
        a.autodetectAnnotations(true);

        mapperInjectionPoint = new MapperInjectionPoint(a);

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
        Writer w = new OutputStreamWriter(out, Charset.forName("UTF-8"));
        w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
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

    final class MapperInjectionPoint extends MapperDelegate {
        public MapperInjectionPoint(Mapper wrapped) {
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
        compatibilityAliases.put(oldClassName,newClass);
    }

    /**
     * Prior to Hudson 1.106, XStream 1.1.x was used which encoded "$" in class names
     * as "-" instead of "_-" that is used now.  Up through Hudson 1.348 compatibility
     * for old serialized data was maintained via {@code XStream11XmlFriendlyMapper}.
     * However, it was found (HUDSON-5768) that this caused fields with "__" to fail
     * deserialization due to double decoding.  Now this class is used for compatibility.
     */
    private class CompatibilityMapper extends MapperWrapper {
        private CompatibilityMapper(Mapper wrapped) {
            super(wrapped);
        }

        @Override
        public Class realClass(String elementName) {
            Class s = compatibilityAliases.get(elementName);
            if (s!=null)    return s;

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
        private final ConcurrentHashMap<Class<?>,Converter> cache =
                new ConcurrentHashMap<Class<?>,Converter>();

        private AssociatedConverterImpl(XStream xstream) {
            this.xstream = xstream;
        }

        private Converter findConverter(Class<?> t) {
            Converter result = cache.get(t);
            if (result != null)
                // ConcurrentHashMap does not allow null, so use this object to represent null
                return result == this ? null : result;
            try {
                if(t==null || t.getClassLoader()==null)
                    return null;
                Class<?> cl = t.getClassLoader().loadClass(t.getName() + "$ConverterImpl");
                Constructor<?> c = cl.getConstructors()[0];

                Class<?>[] p = c.getParameterTypes();
                Object[] args = new Object[p.length];
                for (int i = 0; i < p.length; i++) {
                    if(p[i]==XStream.class || p[i]==XStream2.class)
                        args[i] = xstream;
                    else if(p[i]== Mapper.class)
                        args[i] = xstream.getMapper();
                    else
                        throw new InstantiationError("Unrecognized constructor parameter: "+p[i]);

                }
                ConverterMatcher cm = (ConverterMatcher)c.newInstance(args);
                result = cm instanceof SingleValueConverter
                        ? new SingleValueConverterWrapper((SingleValueConverter)cm)
                        : (Converter)cm;
                cache.put(t, result);
                return result;
            } catch (ClassNotFoundException e) {
                cache.put(t, this);  // See above.. this object in cache represents null
                return null;
            } catch (IllegalAccessException e) {
                IllegalAccessError x = new IllegalAccessError();
                x.initCause(e);
                throw x;
            } catch (InstantiationException e) {
                InstantiationError x = new InstantiationError();
                x.initCause(e);
                throw x;
            } catch (InvocationTargetException e) {
                InstantiationError x = new InstantiationError();
                x.initCause(e);
                throw x;
            }
        }

        public boolean canConvert(Class type) {
            return findConverter(type)!=null;
        }

        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            findConverter(source.getClass()).marshal(source,writer,context);
        }

        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            return findConverter(context.getRequiredType()).unmarshal(reader,context);
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
    public static abstract class PassthruConverter<T> implements Converter {
        private Converter converter;

        public PassthruConverter(XStream2 xstream) {
            converter = xstream.reflectionConverter;
        }

        public boolean canConvert(Class type) {
            // marshal/unmarshal called directly from AssociatedConverterImpl
            return false;
        }

        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            converter.marshal(source, writer, context);
        }

        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            Object obj = converter.unmarshal(reader, context);
            callback((T)obj, context);
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

        @SuppressWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE") // classOwnership checked for null so why does FB complain?
        @Override public String ownerOf(Class<?> clazz) {
            if (classOwnership != null) {
                return classOwnership.ownerOf(clazz);
            }
            if (pm == null) {
                Jenkins j = Jenkins.getInstance();
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
            throw new UnsupportedOperationException("Refusing to marshal for security reasons");
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            throw new ConversionException("Refusing to unmarshal for security reasons");
        }

        @Override
        public boolean canConvert(Class type) {
            if (type == null) {
                return false;
            }
            try {
                ClassFilter.DEFAULT.check(type);
                ClassFilter.DEFAULT.check(type.getName());
            } catch (SecurityException se) {
                // claim we can convert all the scary stuff so we can throw exceptions when attempting to do so
                return true;
            }
            return false;
        }
    }
}
