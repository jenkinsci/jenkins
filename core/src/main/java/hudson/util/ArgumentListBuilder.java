/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.util;

import hudson.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.BitSet;
import java.util.Properties;
import java.util.Map.Entry;
import java.io.Serializable;
import java.io.StringReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.File;
import java.io.StringReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.jvnet.animal_sniffer.IgnoreJRERequirement;

/**
 * Used to build up arguments for a process invocation.
 *
 * @author Kohsuke Kawaguchi
 */
public class ArgumentListBuilder implements Serializable {
    private final List<String> args = new ArrayList<String>();
    private BitSet mask = new BitSet();

    public ArgumentListBuilder add(Object a) {
        return add(a.toString());
    }

    public ArgumentListBuilder add(File f) {
        return add(f.getAbsolutePath());
    }

    public ArgumentListBuilder add(String a) {
        if(a!=null)
            args.add(a);
        return this;
    }

    public ArgumentListBuilder prepend(String... args) {
        // left-shift the mask
        BitSet nm = new BitSet(this.args.size()+args.length);
        for(int i=0; i<this.args.size(); i++)
            nm.set(i+args.length, mask.get(i));
        mask = nm;

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

        for (Entry<Object,Object> entry : load(properties).entrySet()) {
            args.add(prefix + entry.getKey() + "=" + Util.replaceMacro(entry.getValue().toString(),vr));
        }
        return this;
    }

    @IgnoreJRERequirement
    private Properties load(String properties) throws IOException {
        Properties p = new Properties();
        try {
            p.load(new StringReader(properties));
        } catch (NoSuchMethodError e) {
            // load(Reader) method is only available on JDK6.
            // this fall back version doesn't work correctly with non-ASCII characters,
            // but there's no other easy ways out it seems.
            p.load(new ByteArrayInputStream(properties.getBytes()));
        }
        return p;
    }

    public String[] toCommandArray() {
        return args.toArray(new String[args.size()]);
    }
    
    public ArgumentListBuilder clone() {
        ArgumentListBuilder r = new ArgumentListBuilder();
        r.args.addAll(this.args);
        return r;
    }

    /**
     * Re-initializes the arguments list.
     */
    public void clear() {
        args.clear();
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

    /**
     * Returns true if there are any masked arguments.
     * @return true if there are any masked arguments; false otherwise
     */
    public boolean hasMaskedArguments() {
        return mask.length()>0;
    }

    /**
     * Returns an array of booleans where the masked arguments are marked as true
     * @return an array of booleans.
     */
    public boolean[] toMaskArray() {
        boolean[] mask = new boolean[args.size()];
        for( int i=0; i<mask.length; i++)
            mask[i] = this.mask.get(i);
        return mask;
    }

    /**
     * Add a masked argument
     * @param string the argument
     */
    public void addMasked(String string) {
        mask.set(args.size());
        add(string);
    }

    private static final long serialVersionUID = 1L;
}
