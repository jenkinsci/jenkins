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
import hudson.util.VariableResolver;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Arrays;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Environment variables.
 *
 * <p>
 * While all the platforms I tested (Linux 2.6, Solaris, and Windows XP) have the case sensitive
 * environment variable table, Windows batch script handles environment variable in the case preserving
 * but case <b>insensitive</b> way (that is, cmd.exe can get both FOO and foo as environment variables
 * when it's launched, and the "set" command will display it accordingly, but "echo %foo%" results in
 * echoing the value of "FOO", not "foo" &mdash; this is presumably caused by the behavior of the underlying
 * Win32 API <tt>GetEnvironmentVariable</tt> acting in case insensitive way.) Windows users are also
 * used to write environment variable case-insensitively (like %Path% vs %PATH%), and you can see many
 * documents on the web that claims Windows environment variables are case insensitive.
 *
 * <p>
 * So for a consistent cross platform behavior, it creates the least confusion to make the table
 * case insensitive but case preserving.
 *
 * <p>
 * In Jenkins, often we need to build up "environment variable overrides"
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
     * the platform for which this env vars are targeted for, or else we won't know
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

        // because of the backward compatibility, some parts of Jenkins passes
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

    /**
     * Builds an environment variables from an array of the form <tt>"key","value","key","value"...</tt>
     */
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
     * Calculates the order to override variables.
     * 
     * We should override variables in a following order:
     * <ol>
     *   <li>variables that does not contain variable expressions.</li>
     *   <li>variables that refers variables overridden in 1.</li>
     *   <li>variables that refers variables overridden in 2.</li>
     *   <li>...</li>
     *   <li>(last) variables contains '+' (as PATH+MAVEN)</li>
     * </ol>
     * 
     * This class orders variables in a following way:
     * <ol>
     *   <li>scan each overriding variables and list all referred variables (includes indirect references).</li>
     *   <li>sort variables with a number of referring variables (ascending order).</li>
     * </ol>
     *
     * This is package accessible for testing purpose.
     */
    static class OverrideOrderCalculator {
        /**
         * Extract variables referred directly from a variable.
         */
        private static class TraceResolver implements VariableResolver<String> {
            private final Comparator<? super String> comparator;
            public Set<String> referredVariables;
            
            public TraceResolver(Comparator<? super String> comparator) {
                this.comparator = comparator;
                clear();
            }
            
            public void clear() {
                referredVariables = new TreeSet<String>(comparator);
            }
            
            public String resolve(String name) {
                referredVariables.add(name);
                return "";
            }
        }
        
        private final Comparator<? super String> comparator;
        
        private final Map<String,String> overrides;
        /**
         * set of variables that a variable is REFERRING.
         * When A=${B}, refereeSetMap.get("A").contains("B").
         * Also contains indirect references, when A=${B}, B=${C},
         * refereeSetMap.get("A").contains("C").
         */
        private Map<String, Set<String>> refereeSetMap;
        
        /**
         * set of variables that a variable is REFERRED BY.
         * When A=${B}, referrerSetMap.get("B").contains("A").
         * Also contains indirect references, when A=${B}, B=${C},
         * referrerSetMap.get("C").contains("A").
         */
        private Map<String, Set<String>> referrerSetMap;
        
        public OverrideOrderCalculator(EnvVars target, Map<String,String> overrides) {
            comparator = target.comparator();
            this.overrides = overrides;
            refereeSetMap = new TreeMap<String, Set<String>>(comparator);
            referrerSetMap = new TreeMap<String, Set<String>>(comparator);
            scan();
        }
        
        private int getRefereeNum(String variable) {
            if (refereeSetMap.containsKey(variable)) {
                return refereeSetMap.get(variable).size();
            }
            return 0;
        }
        
        public List<String> getOrderedVariableNames() {
            List<String> nameList = new ArrayList<String>(overrides.keySet());
            Collections.sort(nameList, new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    if (o1.indexOf('+') > -1) {
                        if (o2.indexOf('+') > -1) {
                            // ABC+FOO == XYZ+BAR
                            return 0;
                        }
                        // ABC+FOO > BAR
                        return 1;
                    }
                    
                    if (o2.indexOf('+') > -1) {
                        // FOO < ABC+BAR
                        return -1;
                    }
                    
                    // depends on the number of variables each variable refers.
                    return getRefereeNum(o1) - getRefereeNum(o2);
                }
            });
            return nameList;
        }
        
        /**
         * Scan all variables and list all referring variables.
         */
        public void scan() {
            TraceResolver resolver = new TraceResolver(comparator);
            
            for (String currentVar: overrides.keySet()) {
                if (currentVar.indexOf('+') > 0) {
                    // XYZ+AAA variables should be always processed in last.
                    continue;
                }
                resolver.clear();
                Util.replaceMacro(overrides.get(currentVar), resolver);
                
                // Variables directly referred from the current scanning variable.
                Set<String> refereeSet = resolver.referredVariables;
                
                if (refereeSet.isEmpty()) {
                    // nothing to do if this variables does not refer other variables.
                    continue;
                }
                
                // Find indirect referred variables:
                //   A=${B}
                //   CurrentVar=${A}
                //   -> CurrentVar refers B.
                Set<String> indirectRefereeSet = new TreeSet<String>(comparator);
                for (String referee: refereeSet) {
                    if (refereeSetMap.containsKey(referee)) {
                        indirectRefereeSet.addAll(refereeSetMap.get(referee));
                    }
                }
                
                // now contains variables referred both directly and indirectly.
                refereeSet.addAll(indirectRefereeSet);
                
                // Variables refers the current scanning variable.
                // this contains both direct and indirect reference.
                Set<String> referrerSet = referrerSetMap.get(currentVar);
                
                // what I have to do:
                // 1. Create a link between the current scanning variable and referred variables.
                //     1-a. register the current variable as a referrer of referred variables.
                //     1-b. register referred variables as a referee of the current variable.
                // 2. Create links between referring variables and referred variables.
                //     2-a. register referring variables as referrers of referred variables.
                //     2-b. register referred variables as referees of referring variables.
                // 
                // Links between referring variables and the current scanning variable
                // is already created from referring variables.
                for (String referee: refereeSet) {
                    if (!referrerSetMap.containsKey(referee)) {
                        referrerSetMap.put(referee, new TreeSet<String>(comparator));
                    }
                    // 1-a. register the current variable as a referrer of referred variables.
                    referrerSetMap.get(referee).add(currentVar);
                    // 2-b. register referred variables as referees of referring variables.
                    if (referrerSet != null) {
                        referrerSetMap.get(referee).addAll(referrerSet);
                    }
                }
                
                if (!refereeSetMap.containsKey(currentVar)) {
                    refereeSetMap.put(currentVar, new TreeSet<String>(comparator));
                }
                // 1-b. register referred variables as a referee of the current variable.
                refereeSetMap.get(currentVar).addAll(refereeSet);
                
                if (referrerSet != null) {
                    for (String referer: referrerSet) {
                        // 2-b. register referred variables as referees of referring variables.
                        // For referrer refers the current scanning variable,
                        // refereeSetMap.get(referer) always exists.
                        refereeSetMap.get(referer).addAll(refereeSet);
                    }
                }
            }
        }
    }
    

    /**
     * Overrides all values in the map by the given map. Expressions in values will be expanded.
     * See {@link #override(String, String)}.
     * @return this
     */
    public EnvVars overrideExpandingAll(Map<String,String> all) {
        for (String key : new OverrideOrderCalculator(this, all).getOrderedVariableNames()) {
            override(key, expand(all.get(key)));
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
     * Convenience message
     * @since 1.485
     **/
    public String get(String key, String defaultValue) {
        String v = get(key);
        if (v==null)    v=defaultValue;
        return v;
    }

    @Override
    public String put(String key, String value) {
        if (value==null)    throw new IllegalArgumentException("Null value not allowed as an environment variable: "+key);
        return super.put(key,value);
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
     * Creates a magic cookie that can be used as the model environment variable
     * when we later kill the processes.
     */
    public static EnvVars createCookie() {
        return new EnvVars("HUDSON_COOKIE", UUID.randomUUID().toString());
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
     * of the current JVM process. And therefore, it is Jenkins master's environment
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
        if(Main.isUnitTest || Main.isDevelopmentMode)
            // if unit test is launched with maven debug switch,
            // we need to prevent forked Maven processes from seeing it, or else
            // they'll hang
            vars.remove("MAVEN_OPTS");
        return vars;
    }
}
