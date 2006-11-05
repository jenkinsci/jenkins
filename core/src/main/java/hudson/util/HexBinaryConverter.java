package hudson.util;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import hudson.Util;

/**
 * @author Kohsuke Kawaguchi
 */
public class HexBinaryConverter implements Converter {

    public boolean canConvert(Class type) {
        return type==byte[].class;
    }

    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        byte[] data = (byte[]) source;
        writer.setValue(Util.toHexString(data));
    }

    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        String data = reader.getValue(); // needs to be called before hasMoreChildren.

        byte[] r = new byte[data.length()/2];
         for( int i=0; i<data.length(); i+=2 )
             r[i/2] = (byte)Integer.parseInt(data.substring(i,i+2),16);

        return r;
    }
}
