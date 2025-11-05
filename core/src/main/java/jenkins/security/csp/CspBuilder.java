/*
 * The MIT License
 *
 * Copyright (c) 2025, CloudBees, Inc.
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

package jenkins.security.csp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Restricted(Beta.class)
public class CspBuilder {
    public static final Logger LOGGER = Logger.getLogger(CspBuilder.class.getName());
    private Map<String, Set<String>> directives = new HashMap<>();
    private EnumSet<FetchDirective> initializedFDs = EnumSet.noneOf(FetchDirective.class);

    /**
     * These keys cannot be set explicitly, as they're set by Jenkins.
     */
    @Restricted(NoExternalUse.class)
    static final Set<String> PROHIBITED_KEYS = Set.of(Directive.REPORT_URI, Directive.REPORT_TO);

    public CspBuilder withDefaultContributions() {
        Contributor.all().forEach(c -> c.apply(this));
        return this;
    }

    /**
     * Add the given directive and values. If the directive is already present, merge the values.
     * If this is a fetch directive, {@code #add} does not disable inheritance from fallback directives.
     * To disable inheritance for fetch directives, call {@link #initialize(FetchDirective, String...)} instead.
     * <p>
     *     The directives {@link jenkins.security.csp.Directive#REPORT_URI} and
     *     {@link jenkins.security.csp.Directive#REPORT_TO} cannot be set manually, so will be skipped.
     * </p>
     * <p>
     *     Similarly, the value {@link jenkins.security.csp.Directive#NONE} cannot be set and will be skipped.
     *     Instead, call {@link #remove(String, String...)} with a single argument to reset the directive, then
     *     call {@link #initialize(FetchDirective, String...)} with a single argument to disable inheritance.
     * </p>
     *
     * @param directive the directive to add
     * @return this builder
     */
    public CspBuilder add(String directive, String... values) {
        if (PROHIBITED_KEYS.contains(directive)) {
            LOGGER.config("Directive " + directive + " cannot be set manually");
            return this;
        }
        directives.compute(directive, (k, v) -> {
            final List<String> list = new ArrayList<>(Arrays.stream(values).toList());
            if (list.contains(Directive.NONE)) {
                LOGGER.config("Cannot explicitly add 'none'. See " + Directive.class.getName() + "#NONE Javadoc.");
                list.remove(Directive.NONE);
            }
            if (v == null) {
                return new HashSet<>(list);
            } else {
                v.addAll(list);
                return v;
            }
        });
        return this;
    }

    /**
     * Remove the given values from the directive, if present. If the directive does not exist, do nothing.
     * If no values are provided, removes the entire directive.
     *
     * @param directive the directive to remove
     * @param values the values to remove from the directive, or none if the entire directive should be removed.
     * @return this builder
     */
    public CspBuilder remove(String directive, String... values) {
        // TODO Do we even want to support removal?
        if (values.length == 0) {
            if (FetchDirective.isFetchDirective(directive)) {
                initializedFDs.remove(FetchDirective.fromKey(directive));
            }
            directives.remove(directive);
        } else {
            directives.compute(directive, (k, v) -> {
                if (v == null) {
                    return null;
                } else {
                    Arrays.asList(values).forEach(v::remove);
                    return v;
                }
            });
        }
        return this;
    }

    /**
     * Adds an <em>initial value</em> for the specified {@code *-src} directive.
     * Unlike calls to {@link #add(String, String...)}, this disables inheriting from (fetch directive) fallbacks.
     * This can be invoked multiple times, and the merged set of values will be used.
     *
     * @param fetchDirective the directive
     * @param values its initial values
     * @return this builder
     */
    public CspBuilder initialize(FetchDirective fetchDirective, String... values) {
        initializedFDs.add(fetchDirective);
        add(fetchDirective.toKey(), values);
        return this;
    }

    /**
     * Determine the current effective directives.
     * This can be used to inform potential callers of {@link #remove(String, String...)} what to remove.
     *
     * @return the current effective directives
     */
    public List<Directive> getMergedDirectives() {
        List<Directive> result = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : directives.entrySet()) {
            Set<String> effectiveValues = new HashSet<>();
            final String name = entry.getKey();

            // Calculate inherited values from fallback chain
            if (FetchDirective.isFetchDirective(name)) {
                FetchDirective current = FetchDirective.fromKey(name);

                // Check if this directive was initialized
                boolean wasInitialized = initializedFDs.contains(current);

                // If NOT initialized, traverse fallback chain
                if (!wasInitialized) {
                    FetchDirective fallback = current.getFallback();
                    while (fallback != null && !initializedFDs.contains(fallback)) {
                        fallback = fallback.getFallback();
                    }
                    if (fallback == null) {
                        // This could happen if nothing was initialized, in that case, fallback is default-src
                        fallback = FetchDirective.DEFAULT_SRC;
                    }
                    // If we found an initialized fallback, inherit its values
                    if (directives.containsKey(fallback.toKey())) {
                        effectiveValues.addAll(directives.get(fallback.toKey()));
                    }
                }

                effectiveValues.addAll(entry.getValue());
                result.add(new Directive(name, !wasInitialized, List.copyOf(effectiveValues)));
            } else {
                // Non-fetch directives don't inherit
                effectiveValues.addAll(entry.getValue());
                result.add(new Directive(name, false, List.copyOf(effectiveValues)));
            }
        }
        return List.copyOf(result);
    }

    /**
     * Build the final CSP string. Any directives with no values left will have the 'none' value set.
     *
     * @return the CSP string
     */
    public String build() {
        return buildDirectives().entrySet().stream().map(e -> e.getKey() + " " + e.getValue() + ";").collect(Collectors.joining(" "));
    }

    /**
     * Compiles the directives into a map from key (e.g., {@code default-src}) to values (e.g., {@code 'self' 'unsafe-inline'}).
     *
     * @return a map from directive name to its value for all specified directives.
     */
    public Map<String, String> buildDirectives() {
        return getMergedDirectives().stream().sorted(Comparator.comparing(Directive::name)).map(directive -> {
            String name = directive.name();
            List<String> values = directive.values().stream().sorted(String::compareTo).toList();
            if (values.isEmpty() && FetchDirective.isFetchDirective(name)) {
                values = List.of(Directive.NONE);
            }
            return Map.entry(name, String.join(" ", values));
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, TreeMap::new));
    }
}
