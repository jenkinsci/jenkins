package hudson.util;

import hudson.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;
import java.io.Serializable;
import java.io.StringReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Used to build up arguments for a process invocation.
 *
 * @author Kohsuke Kawaguchi
 */
public class ArgumentListBuilder implements Serializable {
    private final List<String> args = new ArrayList<String>();

    public ArgumentListBuilder add(Object a) {
        return add(a.toString());
    }

    public ArgumentListBuilder add(String a) {
        if(a!=null)
            args.add(a);
        return this;
    }

    public ArgumentListBuilder prepend(String... args) {
        this.args.addAll(0, Arrays.asList(args));
        return this;
    }

    /**
     * Adds an argument by quoting it.
     * This is necessary only in a rare circumstance,
     * such as when adding argument for ssh and rsh.
     *
     * Normal process invocations don't need it, because each
     * argument is treated as its own string and never merged into one. 
     */
    public ArgumentListBuilder addQuoted(String a) {
        return add('"'+a+'"');
    }

    public ArgumentListBuilder add(String... args) {
        for (String arg : args) {
            add(arg);
        }
        return this;
    }

    /**
     * Decomposes the given token into multiple arguments by splitting via whitespace.
     */
    public ArgumentListBuilder addTokenized(String s) {
        if(s==null) return this;
        add(Util.tokenize(s));
        return this;
    }

    /**
     * Adds key value pairs as "-Dkey=value -Dkey=value ..."
     *
     * <tt>-D</tt> portion is configurable as the 'prefix' parameter.
     * @since 1.114
     */
    public ArgumentListBuilder addKeyValuePairs(String prefix, Map<String,String> props) {
        for (Entry<String,String> e : props.entrySet())
            add(prefix+e.getKey()+'='+e.getValue());
        return this;
    }

    /**
     * Adds key value pairs as "-Dkey=value -Dkey=value ..." by parsing a given string using {@link Properties}.
     *
     * @param prefix
     *      The '-D' portion of the example.
     * @param properties
     *      The persisted form of {@link Properties}. For example, "abc=def\nghi=jkl". Can be null, in which
     *      case this method becomes no-op.
     * @param vr
     *      {@link VariableResolver} to be performed on the values.
     * @since 1.262
     */
    public ArgumentListBuilder addKeyValuePairsFromPropertyString(String prefix, String properties, VariableResolver vr) throws IOException {
        if(properties==null)    return this;

        Properties p = new Properties();
        try {
            p.load(new StringReader(properties));
        } catch (NoSuchMethodError e) {
            // load(Reader) method is only available on JDK6.
            // this fall back version doesn't work correctly with non-ASCII characters,
            // but there's no other easy ways out it seems.
            p.load(new ByteArrayInputStream(properties.getBytes()));
        }

        for (Entry<Object,Object> entry : p.entrySet()) {
            args.add(prefix + entry.getKey() + "=" + Util.replaceMacro(entry.getValue().toString(),vr));
        }
        return this;
    }

    public String[] toCommandArray() {
        return args.toArray(new String[args.size()]);
    }
    
    public ArgumentListBuilder clone() {
        ArgumentListBuilder r = new ArgumentListBuilder();
        r.args.addAll(this.args);
        return r;
    }

    public List<String> toList() {
        return args;
    }

    public String toStringWithQuote() {
        StringBuilder buf = new StringBuilder();
        for (String arg : args) {
            if(buf.length()>0)  buf.append(' ');

            if(arg.indexOf(' ')>=0 || arg.length()==0)
                buf.append('"').append(arg).append('"');
            else
                buf.append(arg);
        }
        return buf.toString();
    }

    private static final long serialVersionUID = 1L;
}
