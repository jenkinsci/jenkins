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
 * @author Kohsuke Kawaguchi
 */
public class EmulatedEnumConverter implements Converter {

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
        return Enum_.valueOf(type, reader.getValue());
    }

}
