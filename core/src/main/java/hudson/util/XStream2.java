package hudson.util;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.DataHolder;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.core.JVM;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import hudson.model.Hudson;
import hudson.matrix.AxisList;

/**
 * {@link XStream} enhanced for additional Java5 support and improved robustness.
 * @author Kohsuke Kawaguchi
 */
public class XStream2 extends XStream {
    public XStream2() {
        init();
    }

    public XStream2(HierarchicalStreamDriver hierarchicalStreamDriver) {
        super(hierarchicalStreamDriver);
        init();
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, Object root, DataHolder dataHolder) {
        // init() is too early to do this
        // defensive because some use of XStream happens before plugins are initialized.
        Hudson h = Hudson.getInstance();
        if(h!=null && h.pluginManager!=null && h.pluginManager.uberClassLoader!=null) {
            setClassLoader(h.pluginManager.uberClassLoader);
        }

        return super.unmarshal(reader,root,dataHolder);
    }

    @Override
    protected boolean useXStream11XmlFriendlyMapper() {
        return true;
    }

    private void init() {
        registerConverter(new RobustCollectionConverter(getMapper(),getReflectionProvider()),10);
        registerConverter(new CopyOnWriteList.ConverterImpl(getMapper()),10);
        registerConverter(new DescribableList.ConverterImpl(getMapper()),10);
        registerConverter(new AxisList.ConverterImpl(getMapper(),getReflectionProvider()),10);
        registerConverter(new CopyOnWriteMap.Tree.ConverterImpl(getMapper()),10); // needs to override MapConverter

        // this should come after all the XStream's default simpler converters,
        // but before reflection-based one kicks in.
        registerConverter(new AssociatedConverterImpl(),-10);

        // replace default reflection converter
        registerConverter(new RobustReflectionConverter(getMapper(),new JVM().bestReflectionProvider()),-19);
    }

    private static final class AssociatedConverterImpl implements Converter {
        private Converter findConverter(Class t) {
            try {
                if(t==null || t.getClassLoader()==null)
                    return null;
                Class<?> cl = t.getClassLoader().loadClass(t.getName() + "$ConverterImpl");
                return (Converter)cl.newInstance();
            } catch (ClassNotFoundException e) {
                return null;
            } catch (IllegalAccessException e) {
                IllegalAccessError x = new IllegalAccessError();
                x.initCause(e);
                throw x;
            } catch (InstantiationException e) {
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
}
