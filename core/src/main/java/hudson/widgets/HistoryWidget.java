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

package hudson.widgets;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Functions;
import hudson.model.Job;
import hudson.model.ModelObject;
import hudson.model.Queue;
import hudson.util.AlternativeUiTextProvider;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import jenkins.model.HistoricalBuild;
import jenkins.util.SystemProperties;
import jenkins.widgets.HistoryPageEntry;
import jenkins.widgets.HistoryPageFilter;
import jenkins.widgets.WidgetFactory;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.Header;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

/**
 * Displays the history of records on the side panel.
 *
 * @param <O>
 *      Owner of the widget, typically {@link Job}
 * @param <T>
 *      Type individual record, typically {@link HistoricalBuild}
 * @author Kohsuke Kawaguchi
 */
public class HistoryWidget<O extends ModelObject, T> extends Widget {

    /**
     * Replaceable title for describing the kind of tasks this history shows. Defaults to "Build History".
     */
    public static final AlternativeUiTextProvider.Message<HistoryWidget<?, ?>> DISPLAY_NAME = new AlternativeUiTextProvider.Message<>();

    /**
     * The given data model of records. Newer ones first.
     */
    @SuppressFBWarnings(value = "PA_PUBLIC_PRIMITIVE_ATTRIBUTE", justification = "Preserve API compatibility")
    public Iterable<T> baseList;

    /**
     * Indicates the next build number that client ajax should fetch.
     */
    private String nextBuildNumberToFetch;

    /**
     * URL of the {@link #owner}.
     */
    public final String baseUrl;

    public final O owner;

    private boolean trimmed;

    public final Adapter<? super T> adapter;

    final Long newerThan;
    final Long olderThan;
    final String searchString;

    /**
     * First transient build record. Everything >= this will be discarded when AJAX call is made.
     */
    private String firstTransientBuildKey;

    /**
     * @param owner
     *      The parent model object that owns this widget.
     */
    public HistoryWidget(O owner, Iterable<T> baseList, Adapter<? super T> adapter) {
        StaplerRequest2 currentRequest = Stapler.getCurrentRequest2();
        this.adapter = adapter;
        this.baseList = baseList;
        this.baseUrl = Functions.getNearestAncestorUrl(currentRequest, owner);
        this.owner = owner;
        this.newerThan = getPagingParam(currentRequest, "newer-than");
        this.olderThan = getPagingParam(currentRequest, "older-than");
        this.searchString = currentRequest.getParameter("search");
    }

    @Override
    protected String getOwnerUrl() {
        return baseUrl;
    }

    /**
     * Title of the widget.
     */
    public String getDisplayName() {
        return AlternativeUiTextProvider.get(DISPLAY_NAME, this, Messages.BuildHistoryWidget_DisplayName());
    }

    @Override
    public String getUrlName() {
        return "buildHistory";
    }

    public String getFirstTransientBuildKey() {
        return firstTransientBuildKey;
    }

    /**
     * Calculates the first transient build record. Everything ≥ this will be discarded when AJAX call is made.
     *
     * @param historyPageFilter
     *      The history page filter containing the list of builds.
     * @return
     *      The history page filter that was passed in.
     */
    @SuppressWarnings("unchecked") // TODO actually not type-safe
    protected HistoryPageFilter updateFirstTransientBuildKey(HistoryPageFilter historyPageFilter) {
        updateFirstTransientBuildKey(historyPageFilter.runs);
        return historyPageFilter;
    }

    private Iterable<HistoryPageEntry<T>> updateFirstTransientBuildKey(Iterable<HistoryPageEntry<T>> source) {
        String key = null;
        for (HistoryPageEntry<T> t : source) {
            if (adapter.isBuilding(t.getEntry())) {
                key = adapter.getKey(t.getEntry());
            }
        }
        firstTransientBuildKey = key;
        return source;
    }

    /**
     * The records to be rendered this time.
     */
    public Iterable<HistoryPageEntry<T>> getRenderList() {
        if (trimmed) {
            List<HistoryPageEntry<T>> pageEntries = toPageEntries(baseList);
            if (pageEntries.size() > THRESHOLD) {
                return updateFirstTransientBuildKey(pageEntries.subList(0, THRESHOLD));
            } else {
                trimmed = false;
                return updateFirstTransientBuildKey(pageEntries);
            }
        } else {
                // to prevent baseList's concrete type from getting picked up by <j:forEach> in view
            return updateFirstTransientBuildKey(toPageEntries(baseList));
        }
    }

    private List<HistoryPageEntry<T>> toPageEntries(Iterable<T> historyItemList) {
        Iterator<T> iterator = historyItemList.iterator();

        if (!iterator.hasNext()) {
            return Collections.emptyList();
        }

        List<HistoryPageEntry<T>> pageEntries = new ArrayList<>();
        while (iterator.hasNext()) {
            pageEntries.add(new HistoryPageEntry<>(iterator.next()));
        }

        return pageEntries;
    }

    /**
     * Get a {@link jenkins.widgets.HistoryPageFilter} for rendering a page of queue items.
     */
    public HistoryPageFilter<T> getHistoryPageFilter() {
        HistoryPageFilter<T> historyPageFilter = newPageFilter();

        historyPageFilter.add(baseList);
        historyPageFilter.widget = this;
        return updateFirstTransientBuildKey(historyPageFilter);
    }

    protected HistoryPageFilter<T> newPageFilter() {
        HistoryPageFilter<T> historyPageFilter = new HistoryPageFilter<>(THRESHOLD);
        historyPageFilter.widget = this;

        if (newerThan != null) {
            historyPageFilter.setNewerThan(newerThan);
        } else if (olderThan != null) {
            historyPageFilter.setOlderThan(olderThan);
        }

        if (searchString != null) {
            historyPageFilter.setSearchString(searchString);
        }

        return historyPageFilter;
    }

    public boolean isTrimmed() {
        return trimmed;
    }

    public void setTrimmed(boolean trimmed) {
        this.trimmed = trimmed;
    }

    /**
     * Handles AJAX requests from browsers to update build history.
     *
     * @param n
     *      The build 'number' to fetch. This is string because various variants
     *      uses non-numbers as the build key.
     */
    public void doAjax(StaplerRequest2 req, StaplerResponse2 rsp,
          @Header("n") String n) throws IOException, ServletException {

        rsp.setContentType("text/html;charset=UTF-8");

        // pick up builds to send back
        List<T> items = new ArrayList<>();

        if (n != null) {
            String nn = null; // we'll compute next n here

            // list up all builds >=n.
            for (T t : baseList) {
                if (adapter.compare(t, n) >= 0) {
                    items.add(t);
                    if (adapter.isBuilding(t))
                    nn = adapter.getKey(t); // the next fetch should start from youngest build in progress
                } else
                    break;
            }

            if (nn == null) {
                if (items.isEmpty()) {
                    // nothing to report back. next fetch should retry the same 'n'
                    nn = n;
                } else {
                    // every record fetched this time is frozen. next fetch should start from the next build
                    nn = adapter.getNextKey(adapter.getKey(items.get(0)));
                }
            }

            baseList = items;

            rsp.setHeader("n", nn);
            firstTransientBuildKey = nn; // all builds >= nn should be marked transient
        }

        HistoryPageFilter page = getHistoryPageFilter();
        req.getView(page, "ajaxBuildHistory.jelly").forward(req, rsp);
    }

    static final int THRESHOLD = SystemProperties.getInteger(HistoryWidget.class.getName() + ".threshold", 30);

    public String getNextBuildNumberToFetch() {
        return nextBuildNumberToFetch;
    }

    public void setNextBuildNumberToFetch(String nextBuildNumberToFetch) {
        this.nextBuildNumberToFetch = nextBuildNumberToFetch;
    }

    public interface Adapter<T> {
        /**
         * If record is newer than the key, return a positive number.
         */
        int compare(T record, String key);

        String getKey(T record);

        boolean isBuilding(T record);

        String getNextKey(String key);
    }

    private Long getPagingParam(@CheckForNull StaplerRequest2 currentRequest, @CheckForNull String name) {
        if (currentRequest == null || name == null) {
            return null;
        }

        String paramVal = currentRequest.getParameter(name);
        if (paramVal == null) {
            return null;
        }
        try {
            return Long.valueOf(paramVal);
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    @Extension
    @Restricted(DoNotUse.class)
    @Symbol("history")
    public static final class FactoryImpl extends WidgetFactory<Job, HistoryWidget> {
        @Override
        public Class<Job> type() {
            return Job.class;
        }

        @Override
        public Class<HistoryWidget> widgetType() {
            return HistoryWidget.class;
        }

        @NonNull
        @Override
        public Collection<HistoryWidget> createFor(@NonNull Job target) {
            // e.g. hudson.model.ExternalJob
            if (!(target instanceof Queue.Task)) {
                return List.of(new HistoryWidget<>(target, target.getBuilds(), Job.HISTORY_ADAPTER));
            }
            return Collections.emptySet();
        }
    }
}
