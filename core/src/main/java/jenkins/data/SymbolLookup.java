package jenkins.data;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.ParameterValue;
import jenkins.model.Jenkins;
import org.codehaus.groovy.tools.Utilities;
import org.jenkinsci.Symbol;
import org.jvnet.hudson.annotation_indexer.Index;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Finds symbol by its name.
 *
 * @author Kohsuke Kawaguchi
 * @see Symbol#value()
 */
@Extension
public class SymbolLookup {
    private final ConcurrentMap<Key,Object> cache = new ConcurrentHashMap<>();

    private final ConcurrentMap<Key,Object> noHitCache = new ConcurrentHashMap<>();

    static final Object NO_HIT = new Object();

    @Inject
    PluginManager pluginManager;

    @Inject
    Jenkins jenkins;

    Set<String> pluginNames = Collections.EMPTY_SET;

    private static HashSet<String> pluginsToNames(List<PluginWrapper> plugins) {
        HashSet<String> pluginNames = new HashSet<>(plugins.size());
        for (PluginWrapper pw : plugins) {
            pluginNames.add(pw.getShortName());
        }
        return pluginNames;
    }

    /** Update list of plugins used and purge the noHit cache if plugins have been added
     */
    private synchronized void checkPluginsForChangeAndRefresh() {
        List<PluginWrapper> wrap = pluginManager.getPlugins();
        Set<String> names = pluginsToNames(wrap);

        if (wrap.size() != pluginNames.size() || !(pluginNames.containsAll(names))) {
            this.pluginNames = names;
            noHitCache.clear();
            return;
        }
    }

    /**
     * @param type
     *      Restrict the search to a subset of extensions.
     */
    public <T> T find(Class<T> type, String symbol) {
        try {
            Key k = new Key("find",type,symbol);
            Object i = cache.get(k);
            if (i!=null)    return type.cast(i);

            // not allowing @Symbol to use an invalid identifier.
            // TODO: compile time check
            if (!Utilities.isJavaIdentifier(symbol))
                return null;

            // Check for an explicit no-response with the plugin, after confirming no new plugins
            checkPluginsForChangeAndRefresh();
            Object miss = noHitCache.get(k);
            if (miss == NO_HIT) {
                return null;
            }

            for (Class<?> e : Index.list(Symbol.class, pluginManager.uberClassLoader, Class.class)) {
                if (type.isAssignableFrom(e)) {
                    Symbol s = e.getAnnotation(Symbol.class);
                    if (s != null) {
                        for (String t : s.value()) {
                            if (t.equals(symbol)) {
                                i = jenkins.getInjector().getInstance(e);
                                cache.put(k, i);
                                return type.cast(i);
                            }
                        }
                    }
                }
            }

            noHitCache.put(k, NO_HIT);
            return null;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to find @Symbol",e);
            return null;
        }
    }

    /**
     * Looks for a {@link Descriptor} that has the given symbol
     *
     * @param type
     *      Restrict the search to a subset of {@link Describable}
     */
    public Descriptor<?> findDescriptor(Class<?> type, String symbol) {
        try {
            Key k = new Key("findDescriptor",type,symbol);
            Object i = cache.get(k);
            if (i!=null)    return (Descriptor)i;

            // not allowing @Symbol to use an invalid identifier.
            // TODO: compile time check
            if (!Utilities.isJavaIdentifier(symbol))
                return null;

            // Check for an explicit no-response with the plugin, after confirming no new plugins
            checkPluginsForChangeAndRefresh();
            Object miss = noHitCache.get(k);
            if (miss == NO_HIT) {
                return null;
            }

            // TODO add casc logic to infer a "natural" symbol for non annotated descriptors

            // FIXME Question: is Index.list really more efficient vs iterating over known descriptors ?
            for (Class<?> e : Index.list(Symbol.class, pluginManager.uberClassLoader, Class.class)) {
                if (Descriptor.class.isAssignableFrom(e)) {
                    Symbol s = e.getAnnotation(Symbol.class);
                    if (s != null) {
                        for (String t : s.value()) {
                            if (t.equals(symbol)) {
                                Descriptor d = (Descriptor) jenkins.getInjector().getInstance(e);
                                if (type.isAssignableFrom(d.clazz)) {
                                    cache.put(k, d);
                                    return d;
                                }
                            }
                        }
                    }
                }
            }

            noHitCache.put(k, NO_HIT);
            return null;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to find @Symbol",e);
            return null;
        }
    }

    private static class Key {
        private final String tag;
        private final Class type;
        private final String name;

        public Key(String tag, Class type, String name) {
            this.tag = tag;
            this.type = type;
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Key key = (Key) o;
            return type==key.type && tag.equals(key.tag) && name.equals(key.name);
        }

        @Override
        public int hashCode() {
            int h = type.hashCode();
            h = h*31 + tag.hashCode();
            h = h*31 + name.hashCode();
            return h;
        }
    }

    /**
     * Gets the singleton instance.
     */
    public static SymbolLookup get() {
        Jenkins j = Jenkins.getInstance();
        if (j == null) {
            throw new IllegalStateException();
        }
        return j.getInjector().getInstance(SymbolLookup.class);
    }

    /**
     * Get the {@link Symbol} value(s) for the class of the given object, generally a {@link Descriptor}, if the annotation
     * is present. If the object is in fact a {@link Describable}, we'll use its {@link Descriptor} class instead.
     *
     * @param o An object
     * @return The {@link Symbol} annotation value(s) for the class (generally a {@link Descriptor} that object represents,
     * or an empty {@link Set} if the annotation is not present.
     */
    @Nonnull
    public static Set<String> getSymbolValue(@Nonnull Object o) {
        if (o instanceof Describable) {
            return getSymbolValue(((Describable) o).getDescriptor().getClass());
        } else {
            return getSymbolValue(o.getClass());
        }
    }

    /**
     * Get the {@link Symbol} value(s) for the given class, if the annotation is present.
     * This will get the {@link Descriptor} for {@link Describable} classes.
     *
     * @param c A class.
     * @return The {@link Symbol} annotation value(s) for the given class, or an empty {@link Set} if the annotation is not present.
     */
    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE",
            justification = "Jenkins.getInstance() can return null in theory.")
    @Nonnull public static Set<String> getSymbolValue(@Nonnull Class<?> c) {
        Set<String> symbolValues = new LinkedHashSet<>();
        Jenkins j = Jenkins.getInstanceOrNull();
        if (Describable.class.isAssignableFrom(c) && !Descriptor.class.isAssignableFrom(c) && j != null) {
            Descriptor<?> d = j.getDescriptor(c.asSubclass(Describable.class));
            if (d != null) {
                symbolValues.addAll(getSymbolValue(d));
            }
        } else {
            Symbol s = c.getAnnotation(Symbol.class);
            if (s != null) {
                Collections.addAll(symbolValues, s.value());
            } else if (j != null && ParameterValue.class.isAssignableFrom(c)) { // TODO JENKINS-26093 hack, pending core change
                try {
                    symbolValues.addAll(getSymbolValue(c.getClassLoader().loadClass(c.getName().replaceFirst("Value$", "Definition"))));
                } catch (ClassNotFoundException x) {
                    // ignore
                }
            }
        }
        return symbolValues;
    }

    private static final Logger LOGGER = Logger.getLogger(SymbolLookup.class.getName());
}
