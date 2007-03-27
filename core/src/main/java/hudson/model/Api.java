package hudson.model;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.mapper.MapperWrapper;

import java.beans.Introspector;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.net.URL;
import java.lang.reflect.Field;

import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerRequest;
import hudson.util.XStream2;
import net.sf.json.util.JSONBuilder;
import net.sf.json.JSONObject;

/**
 * Used to expose remote access API for ".../api/"
 *
 * @author Kohsuke Kawaguchi
 */
public class Api extends AbstractModelObject {
    /**
     * Value bean to be exposed as XML/JSON/etc.
     */
    private final Object bean;

    public Api(Object bean) {
        this.bean = bean;
    }

    public String getDisplayName() {
        return "API";
    }

    /**
     * Exposes the bean as XML.
     */
    public void doXml(StaplerRequest req, StaplerResponse rsp) throws IOException {
        rsp.setContentType("application/xml;charset=UTF-8");
        RestXStream.INSTANCE.toXML(bean, rsp.getWriter());
    }

    /**
     * Exposes the bean as JSON.
     */
    public void doJson(StaplerRequest req, StaplerResponse rsp) throws IOException {
        rsp.setContentType("text/javascript;charset=UTF-8");

        String pad = req.getParameter("jsonp");
        PrintWriter w = rsp.getWriter();
        if(pad!=null) w.print(pad+'(');
        JSONSerializer.write(bean,new JSONBuilder(w));
        if(pad!=null) w.print(')');
    }

    /**
     * {@link XStream} customized for writing XML for the REST API.
     *
     * @author Kohsuke Kawaguchi
     */
    private static class RestXStream extends XStream2 {
        private RestXStream() {
            setMode(NO_REFERENCES);
            registerConverter(Result.conv);
        }

        @Override
        protected MapperWrapper wrapMapper(MapperWrapper next) {
            return new MapperWrapper(next) {
                // always use the short name
                public String serializedClass(Class type) {
                    return Introspector.decapitalize(type.getSimpleName());
                }

                // don't serialize the outer class
                public boolean shouldSerializeMember(Class definedIn, String fieldName) {
                    return !fieldName.startsWith("this$") && !fieldName.startsWith("val$")
                        && super.shouldSerializeMember(definedIn, fieldName);
                }
            };
        }

        static final RestXStream INSTANCE = new RestXStream();
    }

    /**
     * Works like {@link JSONObject#fromBean(Object)} except
     * this uses fields instead of properties.
     *
     * @author Kohsuke Kawaguchi
     */
    private static class JSONSerializer {

        private static final Set<Class> LEAF_TYPES = new HashSet<Class>(Arrays.asList(
            String.class,
            URL.class,
            Boolean.class,
            Integer.class
        ));

        public static void write(Object bean, JSONBuilder builder) {
            if(bean==null) {
                builder.value(null);
                return;
            }

            Class c = bean.getClass();

            if(LEAF_TYPES.contains(c)) {
                builder.value(bean);
                return;
            }
            if(c.getComponentType()!=null) { // array
                builder.array();
                for (Object item : (Object[])bean)
                    write(item,builder);
                builder.endArray();
                return;
            }
            if(Collection.class.isAssignableFrom(c)) {
                builder.array();
                for (Object item : (Collection) bean)
                    write(item,builder);
                builder.endArray();
                return;
            }
            if(Map.class.isAssignableFrom(c)) {
                builder.object();
                for (Map.Entry e : ((Map<?,?>) bean).entrySet()) {
                    builder.key(e.getKey().toString());
                    write(e.getValue(),builder);
                }
                return;
            }

            // otherwise handle it as a bean
            writeBean(builder, bean);
        }

        private static void writeBean(JSONBuilder builder, Object bean) {
            builder.object();

            for( Field f : bean.getClass().getFields() ) {
                try {
                    Object value = f.get(bean);
                    if(value!=null) {
                        builder.key(f.getName());
                        write(value,builder);
                    }
                } catch (IllegalAccessException e) {
                    // impossible given that this is a public field
                    throw new Error(e);
                }
            }

            builder.endObject();
        }
    }
}
