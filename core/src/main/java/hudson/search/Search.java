/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Yahoo!, Inc.
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

package hudson.search;

import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Util;
import hudson.util.EditDistance;
import io.jenkins.servlet.ServletExceptionWrapper;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.security.stapler.StaplerNotDispatchable;
import jenkins.util.MemoryReductionUtil;
import jenkins.util.SystemProperties;
import org.jenkins.ui.symbol.Symbol;
import org.jenkins.ui.symbol.SymbolRequest;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.export.DataWriter;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Flavor;

/**
 * Web-bound object that provides search/navigation capability.
 *
 * <p>
 * This object is bound to "./search" of a model object via {@link SearchableModelObject} and serves
 * HTTP requests coming from JavaScript to provide search result and auto-completion.
 *
 * @author Kohsuke Kawaguchi
 * @see SearchableModelObject
 */
public class Search implements StaplerProxy {

    /**
     * Limits the maximum number of search results.
     */
    private static /* nonfinal for Jenkins script console */ int MAX_SEARCH_SIZE = Integer.getInteger(Search.class.getName() + ".MAX_SEARCH_SIZE", 500);

    public void doIndex(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        if (Util.isOverridden(Search.class, getClass(), "doIndex", StaplerRequest.class, StaplerResponse.class)) {
            try {
                doIndex(StaplerRequest.fromStaplerRequest2(req), StaplerResponse.fromStaplerResponse2(rsp));
            } catch (javax.servlet.ServletException e) {
                throw ServletExceptionWrapper.toJakartaServletException(e);
            }
        } else {
            doIndexImpl(req, rsp);
        }
    }

    /**
     * @deprecated use {@link #doIndex(StaplerRequest2, StaplerResponse2)}
     */
    @Deprecated
    @StaplerNotDispatchable
    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException, javax.servlet.ServletException {
        try {
            doIndexImpl(StaplerRequest.toStaplerRequest2(req), StaplerResponse.toStaplerResponse2(rsp));
        } catch (ServletException e) {
            throw ServletExceptionWrapper.fromJakartaServletException(e);
        }
    }

    private void doIndexImpl(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        List<Ancestor> l = req.getAncestors();
        for (int i = l.size() - 1; i >= 0; i--) {
            Ancestor a = l.get(i);
            if (a.getObject() instanceof SearchableModelObject) {
                SearchableModelObject smo = (SearchableModelObject) a.getObject();
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine(String.format("smo.displayName=%s, searchName=%s", smo.getDisplayName(), smo.getSearchName()));
                }

                SearchIndex index = smo.getSearchIndex();
                String query = req.getParameter("q");
                if (query != null) {
                    SuggestedItem target = find(index, query, smo);
                    if (target != null) {
                        // found
                        rsp.sendRedirect2(req.getContextPath() + target.getUrl());
                        return;
                    }
                }
            }
        }

        // no exact match. show the suggestions
        rsp.setStatus(SC_NOT_FOUND);
        req.getView(this, "search-failed.jelly").forward(req, rsp);
    }

    /**
     * Used by OpenSearch auto-completion. Returns JSON array of the form:
     *
     * <pre>
     * ["queryString",["comp1","comp2",...]]
     * </pre>
     *
     * See http://developer.mozilla.org/en/docs/Supporting_search_suggestions_in_search_plugins
     */
    public void doSuggestOpenSearch(StaplerRequest2 req, StaplerResponse2 rsp, @QueryParameter String q) throws IOException, ServletException {
        rsp.setContentType(Flavor.JSON.contentType);
        DataWriter w = Flavor.JSON.createDataWriter(null, rsp);
        w.startArray();
        w.value(q);

        w.startArray();
        for (SuggestedItem item : getSuggestions(req, q))
            w.value(item.getPath());
        w.endArray();
        w.endArray();
    }

    /**
     * Used by search box auto-completion. Returns JSON array.
     */
    public void doSuggest(StaplerRequest2 req, StaplerResponse2 rsp, @QueryParameter String query) throws IOException, ServletException {
        Result r = new Result();
        for (SuggestedItem item : getSuggestions(req, query)) {
            String symbolName = item.item.getSearchIcon();

            if (symbolName == null || !symbolName.startsWith("symbol-")) {
                symbolName = "symbol-search";
            }

            r.suggestions.add(new Item(item.getPath(), item.getUrl(),
                    Symbol.get(new SymbolRequest.Builder().withRaw(symbolName).build())));
        }
        rsp.serveExposedBean(req, r, Flavor.JSON);
    }

    /**
     * Gets the list of suggestions that match the given query.
     *
     * @return
     *      can be empty but never null. The size of the list is always smaller than
     *      a certain threshold to avoid showing too many options.
     */
    public SearchResult getSuggestions(StaplerRequest2 req, String query) {
        if (Util.isOverridden(Search.class, getClass(), "getSuggestions", StaplerRequest.class, String.class)) {
            return getSuggestions(StaplerRequest.fromStaplerRequest2(req), query);
        } else {
            return getSuggestionsImpl(req, query);
        }
    }

    /**
     * @deprecated use {@link #getSuggestions(StaplerRequest2, String)}
     */
    @Deprecated
    public SearchResult getSuggestions(StaplerRequest req, String query) {
        return getSuggestionsImpl(StaplerRequest.toStaplerRequest2(req), query);
    }

    private SearchResult getSuggestionsImpl(StaplerRequest2 req, String query) {
        Set<String> paths = new HashSet<>();  // paths already added, to control duplicates
        SearchResultImpl r = new SearchResultImpl();
        int max = Math.min(
                req.hasParameter("max") ? Integer.parseInt(req.getParameter("max")) : 100,
                MAX_SEARCH_SIZE
        );
        SearchableModelObject smo = findClosestSearchableModelObject(req);
        for (SuggestedItem i : suggest(makeSuggestIndex(req), query, smo)) {
            if (r.size() >= max) {
                r.hasMoreResults = true;
                break;
            }
            if (paths.add(i.getPath()))
                r.add(i);
        }
        return r;
    }

    public int getMaxSearchSize() {
        return MAX_SEARCH_SIZE;
    }

    private @CheckForNull SearchableModelObject findClosestSearchableModelObject(StaplerRequest2 req) {
        List<Ancestor> l = req.getAncestors();
        for (int i = l.size() - 1; i >= 0; i--) {
            Ancestor a = l.get(i);
            if (a.getObject() instanceof SearchableModelObject) {
                return (SearchableModelObject) a.getObject();
            }
        }
        return null;
    }

    /**
     * Creates merged search index for suggestion.
     */
    private SearchIndex makeSuggestIndex(StaplerRequest2 req) {
        SearchIndexBuilder builder = new SearchIndexBuilder();
        for (Ancestor a : req.getAncestors()) {
            if (a.getObject() instanceof SearchableModelObject) {
                SearchableModelObject smo = (SearchableModelObject) a.getObject();
                builder.add(smo.getSearchIndex());
            }
        }
        return builder.make();
    }

    private static class SearchResultImpl extends ArrayList<SuggestedItem> implements SearchResult {

        private boolean hasMoreResults = false;

        @Override
        public boolean hasMoreResults() {
            return hasMoreResults;
        }
    }

    @ExportedBean
    public static class Result {
        @Exported
        public List<Item> suggestions = new ArrayList<>();
    }

    @ExportedBean(defaultVisibility = 999)
    public static class Item {

        @Exported
        @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD", justification = "read by Stapler")
        public String name;

        private final String url;

        public final String iconXml;

        public Item(String name) {
            this(name, null, null);
        }

        public Item(String name, String url, String iconXml) {
            this.name = name;
            this.url = url;
            this.iconXml = iconXml;
        }

        @Exported
        public String getUrl() {
            return url;
        }

        @Exported
        public String getIconXml() {
            return iconXml;
        }
    }

    private enum Mode {
        FIND {
            @Override
            void find(SearchIndex index, String token, List<SearchItem> result) {
                index.find(token, result);
            }
        },
        SUGGEST {
            @Override
            void find(SearchIndex index, String token, List<SearchItem> result) {
                index.suggest(token, result);
            }
        };

        abstract void find(SearchIndex index, String token, List<SearchItem> result);

    }

    /**
     * When there are multiple suggested items, this method can narrow down the resultset
     * to the SuggestedItem that has a url that contains the query. This is useful is one
     * job has a display name that matches another job's project name.
     * @param r A list of Suggested items. It is assumed that there is at least one
     * SuggestedItem in r.
     * @param query A query string
     * @return Returns the SuggestedItem which has a search url that contains the query.
     * If no SuggestedItems have a search url which contains the query, then the first
     * SuggestedItem in the List is returned.
     */
    static SuggestedItem findClosestSuggestedItem(List<SuggestedItem> r, String query) {
        for (SuggestedItem curItem : r) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine(String.format("item's searchUrl:%s;query=%s", curItem.item.getSearchUrl(), query));
            }
            if (curItem.item.getSearchUrl().contains(Util.rawEncode(query))) {
                return curItem;
            }
        }

        // couldn't find an item with the query in the url so just
        // return the first one
        return r.get(0);
    }

    /**
     * @deprecated Use {@link Search#find(SearchIndex, String, SearchableModelObject)} instead.
     */
    @Deprecated
    public static SuggestedItem find(SearchIndex index, String query) {
        return find(index, query, null);
    }

    /**
     * Performs a search and returns the match, or null if no match was found
     * or more than one match was found.
     * @since 1.527
     */
    public static SuggestedItem find(SearchIndex index, String query, SearchableModelObject searchContext) {
        List<SuggestedItem> r = find(Mode.FIND, index, query, searchContext);
        if (r.isEmpty()) {
            return null;
        }
        else if (1 == r.size()) {
            return r.get(0);
        }
        else  {
            // we have more than one suggested item, so return the item who's url
            // contains the query as this is probably the job's name
            return findClosestSuggestedItem(r, query);
        }

    }

    /**
     * @deprecated use {@link Search#suggest(SearchIndex, String, SearchableModelObject)} instead.
     */
    @Deprecated
    public static List<SuggestedItem> suggest(SearchIndex index, final String tokenList) {
        return suggest(index, tokenList, null);
    }

    /**
     * @since 1.527
     */
    public static List<SuggestedItem> suggest(SearchIndex index, final String tokenList, SearchableModelObject searchContext) {

        class Tag implements Comparable<Tag> {
            final SuggestedItem item;
            final int distance;
            /** If the path to this suggestion starts with the token list, 1. Otherwise 0. */
            final int prefixMatch;

            Tag(SuggestedItem i) {
                item = i;
                distance = EditDistance.editDistance(i.getPath(), tokenList);
                prefixMatch = i.getPath().startsWith(tokenList) ? 1 : 0;
            }

            @Override
            public int compareTo(Tag that) {
                int r = this.prefixMatch - that.prefixMatch;
                if (r != 0)    return -r;  // ones with head match should show up earlier
                return this.distance - that.distance;
            }
        }

        List<Tag> buf = new ArrayList<>();
        List<SuggestedItem> items = find(Mode.SUGGEST, index, tokenList, searchContext);

        // sort them
        for (SuggestedItem i : items)
            buf.add(new Tag(i));
        Collections.sort(buf);
        items.clear();
        for (Tag t : buf)
            items.add(t.item);

        return items;
    }

    static final class TokenList {
        private final String[] tokens;

        TokenList(String tokenList) {
            tokens = tokenList != null ? tokenList.split("(?<=\\s)(?=\\S)") : MemoryReductionUtil.EMPTY_STRING_ARRAY;
        }

        public int length() { return tokens.length; }

        /**
         * Returns {@link List} such that its {@code get(end)}
         * returns the concatenation of [token_start,...,token_end]
         * (both end inclusive.)
         */
        public List<String> subSequence(final int start) {
            return new AbstractList<>() {
                @Override
                public String get(int index) {
                    StringBuilder buf = new StringBuilder();
                    for (int i = start; i <= start + index; i++)
                        buf.append(tokens[i]);
                    return buf.toString().trim();
                }

                @Override
                public int size() {
                    return tokens.length - start;
                }
            };
        }


        @Override
        public String toString() {
            StringBuilder s = new StringBuilder("TokenList{");
            for (String token : tokens) {
                s.append(token);
                s.append(",");
            }
            s.append('}');

            return s.toString();
        }
    }

    private static List<SuggestedItem> find(Mode m, SearchIndex index, String tokenList, SearchableModelObject searchContext) {
        TokenList tokens = new TokenList(tokenList);
        if (tokens.length() == 0) return Collections.emptyList();   // no tokens given

        List<SuggestedItem>[] paths = new List[tokens.length() + 1]; // we won't use [0].
        for (int i = 1; i <= tokens.length(); i++)
            paths[i] = new ArrayList<>();

        List<SearchItem> items = new ArrayList<>(); // items found in 1 step

        LOGGER.log(Level.FINE, "tokens={0}", tokens);

        // first token
        int w = 1;    // width of token
        for (String token : tokens.subSequence(0)) {
            items.clear();
            m.find(index, token, items);
            for (SearchItem si : items) {
                paths[w].add(SuggestedItem.build(searchContext, si));
                LOGGER.log(Level.FINE, "found search item: {0}", si.getSearchName());
            }
            w++;
        }

        // successive tokens
        for (int j = 1; j < tokens.length(); j++) {
            // for each length
            w = 1;
            for (String token : tokens.subSequence(j)) {
                // for each candidate
                for (SuggestedItem r : paths[j]) {
                    items.clear();
                    m.find(r.item.getSearchIndex(), token, items);
                    for (SearchItem i : items)
                        paths[j + w].add(new SuggestedItem(r, i));
                }
                w++;
            }
        }

        return paths[tokens.length()];
    }

    @Override
    @Restricted(NoExternalUse.class)
    public Object getTarget() {
        if (!SKIP_PERMISSION_CHECK) {
            Jenkins.get().checkPermission(Jenkins.READ);
        }
        return this;
    }

    /**
     * Escape hatch for StaplerProxy-based access control
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    @Restricted(NoExternalUse.class)
    public static /* Script Console modifiable */ boolean SKIP_PERMISSION_CHECK = SystemProperties.getBoolean(Search.class.getName() + ".skipPermissionCheck");

    private static final Logger LOGGER = Logger.getLogger(Search.class.getName());
}
