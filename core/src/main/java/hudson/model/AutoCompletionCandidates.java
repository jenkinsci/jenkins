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

import hudson.search.Search;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Flavor;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.CheckForNull;

/**
 * Data representation of the auto-completion candidates.
 * <p>
 * This object should be returned from your doAutoCompleteXYZ methods.
 *
 * @author Kohsuke Kawaguchi
 */
public class AutoCompletionCandidates implements HttpResponse {
    private final List<String> values = new ArrayList<String>();

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

    public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object o) throws IOException, ServletException {
        Search.Result r = new Search.Result();
        for (String value : values) {
            r.suggestions.add(new hudson.search.Search.Item(value));
        }
        rsp.serveExposedBean(req,r, Flavor.JSON);
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
    public static <T extends Item> AutoCompletionCandidates ofJobNames(final Class<T> type, final String value, @CheckForNull Item self, ItemGroup container) {
        if (self==container)
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
                String n = contextualNameOf(i);
                if ((n.startsWith(value) || value.startsWith(n))
                    // 'foobar' is a valid candidate if the current value is 'foo'.
                    // Also, we need to visit 'foo' if the current value is 'foo/bar'
                 && (value.length()>n.length() || !n.substring(value.length()).contains("/"))
                    // but 'foobar/zot' isn't if the current value is 'foo'
                    // we'll first show 'foobar' and then wait for the user to type '/' to show the rest
                 && i.hasPermission(Item.READ)
                    // and read permission required
                ) {
                    if (type.isInstance(i) && n.startsWith(value))
                        candidates.add(n);

                    // recurse
                    String oldPrefix = prefix;
                    prefix = n;
                    super.onItem(i);
                    prefix = oldPrefix;
                }
            }

            private String contextualNameOf(Item i) {
                if (prefix.endsWith("/") || prefix.length()==0)
                    return prefix+i.getName();
                else
                    return prefix+'/'+i.getName();
            }
        }

        if (container==null || container==Jenkins.getInstance()) {
            new Visitor("").onItemGroup(Jenkins.getInstance());
        } else {
            new Visitor("").onItemGroup(container);
            if (value.startsWith("/"))
                new Visitor("/").onItemGroup(Jenkins.getInstance());

            for ( String p="../"; value.startsWith(p); p+="../") {
                container = ((Item)container).getParent();
                new Visitor(p).onItemGroup(container);
            }
        }

        return candidates;
    }
}
