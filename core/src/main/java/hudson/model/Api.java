package hudson.model;

import hudson.api.DataWriter;
import hudson.api.Exposed;
import hudson.api.Parser;
import hudson.api.ParserBuilder;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Stack;
import java.beans.Introspector;

/**
 * Used to expose remote access API for ".../api/"
 *
 * @author Kohsuke Kawaguchi
 * @see Exposed
 */
public class Api extends AbstractModelObject {
    /**
     * Model object to be exposed as XML/JSON/etc.
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
    public void doXml(StaplerRequest req, final StaplerResponse rsp) throws IOException {
        rsp.setContentType("application/xml;charset=UTF-8");

        write(new DataWriter() {
            private String name = Introspector.decapitalize(bean.getClass().getSimpleName());
            private final Stack<String> objectNames = new Stack<String>();
            private final Stack<Boolean> arrayState = new Stack<Boolean>();
            private final Writer out = rsp.getWriter();
            public boolean isArray;

            public void name(String name) {
                this.name = name;
            }

            public void valuePrimitive(Object v) throws IOException {
                value(v.toString());
            }

            public void value(String v) throws IOException {
                String n = adjustName();
                out.write('<'+n+'>');
                out.write(v);
                out.write("</"+n+'>');
            }

            public void valueNull() {
                // use absence to indicate null.
            }

            public void startArray() {
                // use repeated element to display array
                // this means nested arrays are not supported
                isArray = true;
            }

            public void endArray() {
                isArray = false;
            }

            public void startObject() throws IOException {
                objectNames.push(name);
                arrayState.push(isArray);
                out.write('<'+adjustName()+'>');
            }

            public void endObject() throws IOException {
                name = objectNames.pop();   
                isArray = arrayState.pop();
                out.write("</"+adjustName()+'>');
            }

            /**
             * Returns the name to be used as an element name
             * by considering {@link #isArray}
             */
            private String adjustName() {
                if(isArray) {
                    if(name.endsWith("s"))
                        return name.substring(0,name.length()-1);
                }
                return name;
            }
        });
    }

    /**
     * Exposes the bean as JSON.
     */
    public void doJson(StaplerRequest req, final StaplerResponse rsp) throws IOException {
        rsp.setContentType("text/javascript;charset=UTF-8");

        String pad = req.getParameter("jsonp");
        PrintWriter w = rsp.getWriter();
        if(pad!=null) w.print(pad+'(');

        write(new DataWriter() {
            private boolean needComma;
            private final Writer out = rsp.getWriter();

            public void name(String name) throws IOException {
                comma();
                out.write(name+':');
                needComma = false;
            }

            private void data(String v) throws IOException {
                comma();
                out.write(v);
            }

            private void comma() throws IOException {
                if(needComma) out.write(',');
                needComma = true;
            }

            public void valuePrimitive(Object v) throws IOException {
                data(v.toString());
            }

            public void value(String v) throws IOException {
                data('\"'+v+'\"');
            }

            public void valueNull() throws IOException {
                data("null");
            }

            public void startArray() throws IOException {
                comma();
                out.write('[');
                needComma = false;
            }

            public void endArray() throws IOException {
                out.write(']');
                needComma = true;
            }

            public void startObject() throws IOException {
                comma();
                out.write('{');
                needComma=false;
            }

            public void endObject() throws IOException {
                out.write('}');
                needComma=true;
            }
        });

        if(pad!=null) w.print(')');
    }

    private void write(DataWriter writer) throws IOException {
        Parser p = parserBuilder.get(bean.getClass());
        p.writeTo(bean,writer);
    }

    private static final ParserBuilder parserBuilder = new ParserBuilder();
}
