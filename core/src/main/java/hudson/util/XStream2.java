package hudson.util;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.DataHolder;
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
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
    }
}
