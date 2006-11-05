package hudson.util;

import com.rc.retroweaver.runtime.Enum_;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

/**
 * Converts retroweaver's enum class.
 *
 * <p>
 * Hudson &lt; 1.60 used to store retroweaver's {@link Enum_} class as-is,
 * which was incompatible with how it handles enums in JDK1.5. This converter
 * makes sure that we use the same data format. 
 *
 * @author Kohsuke Kawaguchi
 */
public class RetroweaverEnumConverter implements Converter {

    public boolean canConvert(Class type) {
        return Enum_.class.isAssignableFrom(type);
    }

    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        writer.setValue(((Enum_)source).name());
    }

    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        Class type = context.getRequiredType();
        if (type.getSuperclass() != Enum_.class) {
            type = type.getSuperclass(); // polymorphic enums
        }
        String value = reader.getValue();
        if(value==null || value.trim().length()==0) {
            /*
             backward compatibility mode. read from:

              <mode>
                <description>Leave this machine for tied jobs only</description>
                <ordinal>1</ordinal>
                <name>EXCLUSIVE</name>
              </mode>
            */

            while(reader.hasMoreChildren()) {
                reader.moveDown();
                if(reader.getNodeName().equals("name"))
                    value = reader.getValue();
                reader.moveUp();
            }
        }

        return Enum_.valueOf(type, value);
    }
}
