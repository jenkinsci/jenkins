package hudson.util;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import net.sf.retrotranslator.runtime.java.lang.Enum_;

/**
 * Converts retrotranslator's enum class to make it match with JDK5's native data format.
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class RetrotranslatorEnumConverter implements Converter {

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
             backward compatibility mode. read retroweaver's format from:

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
