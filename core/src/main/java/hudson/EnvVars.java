/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Red Hat, Inc.
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
package hudson;

import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.util.CaseInsensitiveComparator;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.Arrays;

/**
 * Environment variables.
 *
 * <p>
 * In Hudson, often we need to build up "environment variable overrides"
 * on master, then to execute the process on slaves. This causes a problem
 * when working with variables like <tt>PATH</tt>. So to make this work,
 * we introduce a special convention <tt>PATH+FOO</tt> &mdash; all entries
 * that starts with <tt>PATH+</tt> are merged and prepended to the inherited
 * <tt>PATH</tt> variable, on the process where a new process is executed. 
 * 
 * @author Kohsuke Kawaguchi
 */
public class EnvVars extends TreeMap<String,String> {
    /**
     * If this {@link EnvVars} object represents the whole environment variable set,
     * not just a partial list used for overriding later, then we need to know
     * the platform for which this env vars are targeted for, or else we won't konw
     * how to merge variables properly.
     *
     * <p>
     * So this property remembers that information.
     */
    private Platform platform;

    public EnvVars() {
        super(CaseInsensitiveComparator.INSTANCE);
    }

    public EnvVars(Map<String,String> m) {
        this();
        putAll(m);

        // because of the backward compatibility, some parts of Hudson passes
        // EnvVars as Map<String,String> so downcasting is safer.
        if (m instanceof EnvVars) {
            EnvVars lhs = (EnvVars) m;
            this.platform = lhs.platform;
        }
    }

    public EnvVars(EnvVars m) {
        // this constructor is so that in future we can get rid of the downcasting.
        this((Map)m);
    }

    public EnvVars(String... keyValuePairs) {
        this();
        if(keyValuePairs.length%2!=0)
            throw new IllegalArgumentException(Arrays.asList(keyValuePairs).toString());
        for( int i=0; i<keyValuePairs.length; i+=2 )
            put(keyValuePairs[i],keyValuePairs[i+1]);
    }

    /**
     * Overrides the current entry by the given entry.
     *
     * <p>
     * Handles <tt>PATH+XYZ</tt> notation.
     */
    public void override(String key, String value) {
        if(value==null || value.length()==0) {
            remove(key);
            return;
        }

        int idx = key.indexOf('+');
        if(idx>0) {
            String realKey = key.substring(0,idx);
            String v = get(realKey);
            if(v==null) v=value;
            else {
                // we might be handling environment variables for a slave that can have different path separator
                // than the master, so the following is an attempt to get it right.
                // it's still more error prone that I'd like.
                char ch = platform==null ? File.pathSeparatorChar : platform.pathSeparator;
                v=value+ch+v;
            }
            put(realKey,v);
            return;
        }

        put(key,value);
    }

    /**
     * Overrides all values in the map by the given map.
     * See {@link #override(String, String)}.
     * @return this
     */
    public EnvVars overrideAll(Map<String,String> all) {
        for (Map.Entry<String, String> e : all.entrySet()) {
            override(e.getKey(),e.getValue());
        }
        return this;
    }

    /**
     * Resolves environment variables against each other.
     */
	public static void resolve(Map<String, String> env) {
		for (Map.Entry<String,String> entry: env.entrySet()) {
			entry.setValue(Util.replaceMacro(entry.getValue(), env));
		}
	}
    
    /**
     * Takes a string that looks like "a=b" and adds that to this map.
     */
    public void addLine(String line) {
        int sep = line.indexOf('=');
        if(sep > 0) {
            put(line.substring(0,sep),line.substring(sep+1));
        }
    }

    /**
     * Expands the variables in the given string by using environment variables represented in 'this'.
     */
    public String expand(String s) {
        return Util.replaceMacro(s, this);
    }

    /**
     * Obtains the environment variables of a remote peer.
     *
     * @param channel
     *      Can be null, in which case the map indicating "N/A" will be returned.
     * @return
     *      A fresh copy that can be owned and modified by the caller.
     */
    public static EnvVars getRemote(VirtualChannel channel) throws IOException, InterruptedException {
        if(channel==null)
            return new EnvVars("N/A","N/A");
        return channel.call(new GetEnvVars());
    }

    private static final class GetEnvVars implements Callable<EnvVars,RuntimeException> {
        public EnvVars call() {
            return new EnvVars(EnvVars.masterEnvVars);
        }
        private static final long serialVersionUID = 1L;
    }

    /**
     * Environmental variables that we've inherited.
     *
     * <p>
     * Despite what the name might imply, this is the environment variable
     * of the current JVM process. And therefore, it is Hudson master's environment
     * variables only when you access this from the master.
     *
     * <p>
     * If you access this field from slaves, then this is the environment
     * variable of the slave agent.
     */
    public static final Map<String,String> masterEnvVars = initMaster();

    private static EnvVars initMaster() {
        EnvVars vars = new EnvVars(System.getenv());
        vars.platform = Platform.current();
        if(Functions.isUnitTest)
            // if unit test is launched with maven debug switch,
            // we need to prevent forked Maven processes from seeing it, or else
            // they'll hang
            vars.remove("MAVEN_OPTS");
        return vars;
    }
}
