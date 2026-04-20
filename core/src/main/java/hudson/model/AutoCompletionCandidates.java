/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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

package hudson.model;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.search.Search;
import hudson.search.UserSearchProperty;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.export.Flavor;

/**
 * Data representation of the auto-completion candidates.
 * <p>
 * This object should be returned from your doAutoCompleteXYZ methods.
 *
 * @author Kohsuke Kawaguchi
 */
public class AutoCompletionCandidates implements HttpResponse {
    private final List<String> values = new ArrayList<>();

    public AutoCompletionCandidates add(String v) {
        values.add(v);
        return this;
    }

    public AutoCompletionCandidates add(String... v) {
        values.addAll(Arrays.asList(v));
        return this;
    }

    /**
     * Exposes the raw value, in case you want to modify {@link List} directly.
     * @since 1.402
     */
    public List<String> getValues() {
        return values;
    }

    @Override
    public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, Object o) throws IOException, ServletException {
        Search.Result r = new Search.Result();
        for (String value : values) {
            r.suggestions.add(new hudson.search.Search.Item(value));
        }
        rsp.serveExposedBean(req, r, Flavor.JSON);
    }

    /**
     * Auto-completes possible job names.
     *
     * @param type
     *      Limit the auto-completion to the subtype of this type.
     * @param value
     *      The value the user has typed in. Matched as a prefix.
     * @param self
     *      The contextual item for which the auto-completion is provided to.
     *      For example, if you are configuring a job, this is the job being configured.
     * @param container
     *      The nearby contextual {@link ItemGroup} to resolve relative job names from.
     * @since 1.489
     */
    @SuppressFBWarnings(value = "EC_UNRELATED_TYPES_USING_POINTER_EQUALITY", justification = "TODO needs triage")
    public static <T extends Item> AutoCompletionCandidates ofJobNames(final Class<T> type, final String value, @CheckForNull Item self, ItemGroup container) {
        if (self == container)
            container = self.getParent();
        return ofJobNames(type, value, container);
    }


    /**
     * Auto-completes possible job names.
     *
     * @param type
     *      Limit the auto-completion to the subtype of this type.
     * @param value
     *      The value the user has typed in. Matched as a prefix.
     * @param container
     *      The nearby contextual {@link ItemGroup} to resolve relative job names from.
     * @since 1.553
     */
    public static  <T extends Item> AutoCompletionCandidates ofJobNames(final Class<T> type, final String value, ItemGroup container) {
        final AutoCompletionCandidates candidates = new AutoCompletionCandidates();
        class Visitor extends ItemVisitor {
            String prefix;

            Visitor(String prefix) {
                this.prefix = prefix;
            }

            @Override
            public void onItem(Item i) {
                String itemName = contextualNameOf(i);

                //Check user's setting on whether to do case sensitive comparison, configured in user -> configure
                //This is the same setting that is used by the global search field, should be consistent throughout
                //the whole application.
                boolean caseInsensitive = UserSearchProperty.isCaseInsensitive();

                if ((startsWithImpl(itemName, value, caseInsensitive) || startsWithImpl(value, itemName, caseInsensitive))
                    // 'foobar' is a valid candidate if the current value is 'foo'.
                    // Also, we need to visit 'foo' if the current value is 'foo/bar'
                 && (value.length() > itemName.length() || !itemName.substring(value.length()).contains("/"))
                    // but 'foobar/zot' isn't if the current value is 'foo'
                    // we'll first show 'foobar' and then wait for the user to type '/' to show the rest
                 && i.hasPermission(Item.READ)
                    // and read permission required
                ) {
                    if (type.isInstance(i) && startsWithImpl(itemName, value, caseInsensitive))
                        candidates.add(itemName);

                    // recurse
                    String oldPrefix = prefix;
                    prefix = itemName;
                    super.onItem(i);
                    prefix = oldPrefix;
                }
            }

            private String contextualNameOf(Item i) {
                if (prefix.endsWith("/") || prefix.isEmpty())
                    return prefix + i.getName();
                else
                    return prefix + '/' + i.getName();
            }
        }

        if (container == null || container == Jenkins.get()) {
            new Visitor("").onItemGroup(Jenkins.get());
        } else {
            new Visitor("").onItemGroup(container);
            if (value.startsWith("/"))
                new Visitor("/").onItemGroup(Jenkins.get());

            for (String p = "../"; value.startsWith(p); p += "../") {
                container = ((Item) container).getParent();
                new Visitor(p).onItemGroup(container);
            }
        }

        return candidates;
    }

    private static boolean startsWithImpl(String str, String prefix, boolean ignoreCase) {
        return ignoreCase ? str.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)) : str.startsWith(prefix);
    }
}
