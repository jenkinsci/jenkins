package hudson.api;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.IOException;

/**
 * Writes all the property of one {@link ExposedBean} to {@link DataWriter}.
 *
 * @author Kohsuke Kawaguchi
 */
public class Parser<T> {
    private final Class<T> type;

    /**
     * {@link Parser} for the super class.
     */
    private final Parser<? super T> superParser;

    private final Property[] properties;

    /*package*/ Parser(ParserBuilder parent, Class<T> type) {
        this.type = type;
        if(type.getAnnotation(ExposedBean.class)==null)
            throw new IllegalArgumentException(type+" doesn't have @ExposedBean");
        
        parent.parsers.put(type,this);

        Class<? super T> sc = type.getSuperclass();
        if(sc!=null && sc.getAnnotation(ExposedBean.class)!=null)
            superParser = parent.get(sc);
        else
            superParser = null;

        List<Property> properties = new ArrayList<Property>();

        // Use reflection to find out what properties are exposed.
        for( Field f : type.getFields() ) {
            if(f.getDeclaringClass()!=type) continue;
            Exposed exposed = f.getAnnotation(Exposed.class);
            if(exposed !=null)
                properties.add(new FieldProperty(parent,f,exposed));
        }

        for( Method m : type.getMethods() ) {
            if(m.getDeclaringClass()!=type) continue;
            Exposed exposed = m.getAnnotation(Exposed.class);
            if(exposed !=null)
                properties.add(new MethodProperty(parent,m,exposed));
        }

        this.properties = properties.toArray(new Property[properties.size()]);
        Arrays.sort(this.properties);
    }

    /**
     * Writes the property values of the given object to the writer.
     */
    public void writeTo(T object, DataWriter writer) throws IOException {
        writer.startObject();
        writeTo(object,1,writer);
        writer.endObject();
    }

    void writeTo(T object, int depth, DataWriter writer) throws IOException {
        if(superParser!=null)
            superParser.writeTo(object,depth,writer);

        for (Property p : properties)
            p.writeTo(object,depth,writer);
    }
}
