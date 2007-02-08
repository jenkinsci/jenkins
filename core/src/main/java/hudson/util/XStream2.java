package hudson.util;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.DataHolder;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import hudson.model.Hudson;

/**
 * {@link XStream} enhanced for retroweaver support.
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

    private void init() {
        registerConverter(new RobustCollectionConverter(getClassMapper()),10);
        registerConverter(new RetroweaverEnumConverter(),10);
        registerConverter(new RetrotranslatorEnumConverter(),10);
        registerConverter(new CopyOnWriteList.ConverterImpl(getClassMapper()),10);
        registerConverter(new DescribableList.ConverterImpl(getClassMapper()),10);

        // this should come after all the XStream's default simpler converters,
        // but before reflection-based one kicks in.
        registerConverter(new AssociatedConverterImpl(),-10);
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
