package hudson.api;

import hudson.util.IOException2;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Exposes one {@link Exposed exposed property} of {@link ExposedBean} to
 * {@link DataWriter}.
 *  
 * @author Kohsuke Kawaguchi
 */
abstract class Property implements Comparable<Property> {
    final String name;
    final ParserBuilder owner;
    final int visibility;

    Property(Parser parent, String name, Exposed exposed) {
        this.owner = parent.parent;
        this.name = exposed.name().length()>1 ? exposed.name() : name;
        int v = exposed.visibility();
        if(v==0)
            v = parent.defaultVisibility;
        this.visibility = v;
    }

    public int compareTo(Property that) {
        return this.name.compareTo(that.name);
    }

    /**
     * Writes one property of the given object to {@link DataWriter}.
     */
    public void writeTo(Object object, int depth, DataWriter writer) throws IOException {
        if(visibility<depth)    return; // not visible

        try {
            writer.name(name);
            writeValue(getValue(object),depth,writer);
        } catch (IllegalAccessException e) {
            throw new IOException2("Failed to write "+name,e);
        } catch (InvocationTargetException e) {
            throw new IOException2("Failed to write "+name,e);
        }
    }

    /**
     * Writes one value of the property to {@link DataWriter}.
     */
    private void writeValue(Object value, int depth, DataWriter writer) throws IOException {
        if(value==null) {
            writer.valueNull();
            return;
        }

        if(value instanceof CustomExposureBean) {
            writeValue(((CustomExposureBean)value).toExposedObject(),depth,writer);
            return;
        }

        Class c = value.getClass();

        if(STRING_TYPES.contains(c)) {
            writer.value(value.toString());
            return;
        }
        if(PRIMITIVE_TYPES.contains(c)) {
            writer.valuePrimitive(value);
            return;
        }
        if(c.getComponentType()!=null) { // array
            writer.startArray();
            for (Object item : (Object[]) value)
                writeValue(item,depth,writer);
            writer.endArray();
            return;
        }
        if(value instanceof Collection) {
            writer.startArray();
            for (Object item : (Collection) value)
                writeValue(item,depth,writer);
            writer.endArray();
            return;
        }
        if(value instanceof Map) {
            writer.startObject();
            for (Map.Entry e : ((Map<?,?>) value).entrySet()) {
                writer.name(e.getKey().toString());
                writeValue(e.getValue(),depth,writer);
            }
            writer.endObject();
            return;
        }
        if(value instanceof Calendar) {
            writer.valuePrimitive(((Calendar) value).getTimeInMillis());
            return;
        }
        if(value instanceof Enum) {
            writer.value(value.toString());
            return;
        }

        // otherwise handle it as a bean
        writer.startObject();
        owner.get(c).writeTo(value,depth+1,writer);
        writer.endObject();
    }

    /**
     * Gets the value of this property from the bean.
     */
    protected abstract Object getValue(Object bean) throws IllegalAccessException, InvocationTargetException;

    private static final Set<Class> STRING_TYPES = new HashSet<Class>(Arrays.asList(
        String.class,
        URL.class
    ));

    private static final Set<Class> PRIMITIVE_TYPES = new HashSet<Class>(Arrays.asList(
        Integer.class,
        Long.class,
        Boolean.class
    ));
}
