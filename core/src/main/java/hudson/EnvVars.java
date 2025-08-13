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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.remoting.VirtualChannel;
import hudson.util.CyclicGraphDetector;
import hudson.util.CyclicGraphDetector.CycleDetectedException;
import hudson.util.VariableResolver;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.logging.Logger;
import jenkins.security.MasterToSlaveCallable;

/**
 * Environment variables.
 *
 * <p>
 * While all the platforms I tested (Linux 2.6, Solaris, and Windows XP) have the case sensitive
 * environment variable table, Windows batch script handles environment variable in the case preserving
 * but case <b>insensitive</b> way (that is, cmd.exe can get both FOO and foo as environment variables
 * when it's launched, and the "set" command will display it accordingly, but "echo %foo%" results in
 * echoing the value of "FOO", not "foo" &mdash; this is presumably caused by the behavior of the underlying
 * Win32 API {@code GetEnvironmentVariable} acting in case insensitive way.) Windows users are also
 * used to write environment variable case-insensitively (like %Path% vs %PATH%), and you can see many
 * documents on the web that claims Windows environment variables are case insensitive.
 *
 * <p>
 * So for a consistent cross platform behavior, it creates the least confusion to make the table
 * case insensitive but case preserving.
 *
 * <p>
 * In Jenkins, often we need to build up "environment variable overrides"
 * on the controller, then to execute the process on agents. This causes a problem
 * when working with variables like {@code PATH}. So to make this work,
 * we introduce a special convention {@code PATH+FOO} &mdash; all entries
 * that starts with {@code PATH+} are merged and prepended to the inherited
 * {@code PATH} variable, on the process where a new process is executed.
 *
 * @author Kohsuke Kawaguchi
 */
public class EnvVars extends TreeMap<String, String> {
    private static final long serialVersionUID = 4320331661987259022L;
    private static Logger LOGGER = Logger.getLogger(EnvVars.class.getName());
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

    /**
     * Gets the platform for which these env vars targeted.
     * @since 2.144
     * @return The platform.
     */
    public @CheckForNull Platform getPlatform() {
        return platform;
    }

    /**
     * Sets the platform for which these env vars target.
     * @since 2.144
     * @param platform the platform to set.
     */
    public void setPlatform(@NonNull Platform platform) {
        this.platform = platform;
    }

    public EnvVars() {
        super(String.CASE_INSENSITIVE_ORDER);
    }

    public EnvVars(@NonNull Map<String, String> m) {
        this();
        putAll(m);

        // because of the backward compatibility, some parts of Jenkins passes
        // EnvVars as Map<String,String> so downcasting is safer.
        if (m instanceof EnvVars lhs) {
            this.platform = lhs.platform;
        }
    }

    @SuppressWarnings("CopyConstructorMissesField") // does not set #platform, see its Javadoc
    public EnvVars(@NonNull EnvVars m) {
        // this constructor is so that in future we can get rid of the downcasting.
        this((Map) m);
    }

    /**
     * Builds an environment variables from an array of the form {@code "key","value","key","value"...}
     */
    public EnvVars(String... keyValuePairs) {
        this();
        if (keyValuePairs.length % 2 != 0)
            throw new IllegalArgumentException(Arrays.asList(keyValuePairs).toString());
        for (int i = 0; i < keyValuePairs.length; i += 2)
            put(keyValuePairs[i], keyValuePairs[i + 1]);
    }

    /**
     * Overrides the current entry by the given entry.
     *
     * <p>
     * Handles {@code PATH+XYZ} notation.
     */
    public void override(String key, String value) {
        if (value == null || value.isEmpty()) {
            remove(key);
            return;
        }

        int idx = key.indexOf('+');
        if (idx > 0) {
            String realKey = key.substring(0, idx);
            String v = get(realKey);
            if (v == null) v = value;
            else {
                // we might be handling environment variables for a agent that can have different path separator
                // than the controller, so the following is an attempt to get it right.
                // it's still more error prone that I'd like.
                char ch = platform == null ? File.pathSeparatorChar : platform.pathSeparator;
                v = value + ch + v;
            }
            put(realKey, v);
            return;
        }

        put(key, value);
    }

    /**
     * Overrides all values in the map by the given map.
     * See {@link #override(String, String)}.
     * @return this
     */
    public EnvVars overrideAll(Map<String, String> all) {
        for (Map.Entry<String, String> e : all.entrySet()) {
            override(e.getKey(), e.getValue());
        }
        return this;
    }

    /**
     * Calculates the order to override variables.
     *
     * Sort variables with topological sort with their reference graph.
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

            TraceResolver(Comparator<? super String> comparator) {
                this.comparator = comparator;
                clear();
            }

            public void clear() {
                referredVariables = new TreeSet<>(comparator);
            }

            @Override
            public String resolve(String name) {
                referredVariables.add(name);
                return "";
            }
        }

        private static class VariableReferenceSorter extends CyclicGraphDetector<String> {
            // map from a variable to a set of variables that variable refers.
            private final Map<String, Set<String>> refereeSetMap;

            VariableReferenceSorter(Map<String, Set<String>> refereeSetMap) {
                this.refereeSetMap = refereeSetMap;
            }

            @Override
            protected Iterable<? extends String> getEdges(String n) {
                // return variables referred from the variable.
                if (!refereeSetMap.containsKey(n)) {
                    // there is a case a non-existing variable is referred...
                    return Collections.emptySet();
                }
                return refereeSetMap.get(n);
            }
        }

        private final Comparator<? super String> comparator;

        @NonNull
        private final EnvVars target;
        @NonNull
        private final Map<String, String> overrides;

        private Map<String, Set<String>> refereeSetMap;
        private List<String> orderedVariableNames;

        OverrideOrderCalculator(@NonNull EnvVars target, @NonNull Map<String, String> overrides) {
            comparator = target.comparator();
            this.target = target;
            this.overrides = overrides;
            scan();
        }

        public List<String> getOrderedVariableNames() {
            return orderedVariableNames;
        }

        // Cut the reference to the variable in a cycle.
        private void cutCycleAt(String referee, List<String> cycle) {
            // cycle contains variables in referrer-to-referee order.
            // This should not be negative, for the first and last one is same.
            int refererIndex = cycle.lastIndexOf(referee) - 1;

            assert refererIndex >= 0;
            String referrer = cycle.get(refererIndex);
            boolean removed = refereeSetMap.get(referrer).remove(referee);
            assert removed;
            LOGGER.warning(String.format("Cyclic reference detected: %s", String.join(" -> ", cycle)));
            LOGGER.warning(String.format("Cut the reference %s -> %s", referrer, referee));
        }

        // Cut the variable reference in a cycle.
        private void cutCycle(List<String> cycle) {
            // if an existing variable is contained in that cycle,
            // cut the cycle with that variable:
            // existing:
            //   PATH=/usr/bin
            // overriding:
            //   PATH1=/usr/local/bin:${PATH}
            //   PATH=/opt/something/bin:${PATH1}
            // then consider reference PATH1 -> PATH can be ignored.
            for (String referee : cycle) {
                if (target.containsKey(referee)) {
                    cutCycleAt(referee, cycle);
                    return;
                }
            }

            // if not, cut the reference to the first one.
            cutCycleAt(cycle.get(0), cycle);
        }

        /**
         * Scan all variables and list all referring variables.
         */
        public void scan() {
            refereeSetMap = new TreeMap<>(comparator);
            List<String> extendingVariableNames = new ArrayList<>();

            TraceResolver resolver = new TraceResolver(comparator);

            for (Map.Entry<String, String> entry : overrides.entrySet()) {
                if (entry.getKey().indexOf('+') > 0) {
                    // XYZ+AAA variables should be always processed in last.
                    extendingVariableNames.add(entry.getKey());
                    continue;
                }
                resolver.clear();
                Util.replaceMacro(entry.getValue(), resolver);

                // Variables directly referred from the current scanning variable.
                Set<String> refereeSet = resolver.referredVariables;
                // Ignore self reference.
                refereeSet.remove(entry.getKey());
                refereeSetMap.put(entry.getKey(), refereeSet);
            }

            VariableReferenceSorter sorter;
            while (true) {
                sorter = new VariableReferenceSorter(refereeSetMap);
                try {
                    sorter.run(refereeSetMap.keySet());
                } catch (CycleDetectedException e) {
                    // cyclic reference found.
                    // cut the cycle and retry.
                    @SuppressWarnings("unchecked")
                    List<String> cycle = e.cycle;
                    cutCycle(cycle);
                    continue;
                }
                break;
            }

            // When A refers B, the last appearance of B always comes after
            // the last appearance of A.
            List<String> reversedDuplicatedOrder = new ArrayList<>(sorter.getSorted());
            Collections.reverse(reversedDuplicatedOrder);

            orderedVariableNames = new ArrayList<>(overrides.size());
            for (String key : reversedDuplicatedOrder) {
                if (overrides.containsKey(key) && !orderedVariableNames.contains(key)) {
                    orderedVariableNames.add(key);
                }
            }
            Collections.reverse(orderedVariableNames);
            orderedVariableNames.addAll(extendingVariableNames);
        }
    }


    /**
     * Overrides all values in the map by the given map. Expressions in values will be expanded.
     * See {@link #override(String, String)}.
     * @return {@code this}
     */
    public EnvVars overrideExpandingAll(@NonNull Map<String, String> all) {
        for (String key : new OverrideOrderCalculator(this, all).getOrderedVariableNames()) {
            override(key, expand(all.get(key)));
        }
        return this;
    }

    /**
     * Resolves environment variables against each other.
     */
    public static void resolve(Map<String, String> env) {
        for (Map.Entry<String, String> entry : env.entrySet()) {
            entry.setValue(Util.replaceMacro(entry.getValue(), env));
        }
    }

    /**
     * Convenience message
     * @since 1.485
     **/
    public String get(String key, String defaultValue) {
        String v = get(key);
        if (v == null)    v = defaultValue;
        return v;
    }

    @Override
    public String put(String key, String value) {
        if (value == null)    throw new IllegalArgumentException("Null value not allowed as an environment variable: " + key);
        return super.put(key, value);
    }

    /**
     * Add a key/value but only if the value is not-null. Otherwise no-op.
     * @since 1.556
     */
    public void putIfNotNull(String key, String value) {
        if (value != null)
            put(key, value);
    }

    /**
     * Add entire map but filter null values out.
     * @since 2.214
     */
    public void putAllNonNull(Map<String, String> map) {
        map.forEach(this::putIfNotNull);
    }


    /**
     * Takes a string that looks like "a=b" and adds that to this map.
     */
    public void addLine(String line) {
        int sep = line.indexOf('=');
        if (sep > 0) {
            put(line.substring(0, sep), line.substring(sep + 1));
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
        if (channel == null)
            return new EnvVars("N/A", "N/A");
        return channel.call(new GetEnvVars());
    }

    private static final class GetEnvVars extends MasterToSlaveCallable<EnvVars, RuntimeException> {
        @Override
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
     * of the current JVM process. And therefore, it is the Jenkins controller's
     * environment variables only when you access this from the controller.
     *
     * <p>
     * If you access this field from agents, then this is the environment
     * variable of the agent.
     */
    public static final Map<String, String> masterEnvVars = initMaster();

    private static EnvVars initMaster() {
        EnvVars vars = new EnvVars(System.getenv());
        vars.platform = Platform.current();
        if (Main.isUnitTest || Main.isDevelopmentMode)
            // if unit test is launched with maven debug switch,
            // we need to prevent forked Maven processes from seeing it, or else
            // they'll hang
            vars.remove("MAVEN_OPTS");
        return vars;
    }
}
