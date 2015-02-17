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

import hudson.Functions;
import hudson.model.ModelObject;
import hudson.model.Run;
import hudson.util.Iterators;

import org.apache.commons.collections.IteratorUtils;
import org.kohsuke.stapler.Header;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

/**
 * Displays the history of records (normally {@link Run}s) on the side panel.
 *
 * @param <O>
 *      Owner of the widget.
 * @param <T>
 *      Type individual record.
 * @author Kohsuke Kawaguchi
 */
public class HistoryWidget<O extends ModelObject,T> extends Widget {
    /**
     * The given data model of records. Newer ones first.
     */
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
	StaplerRequest currentRequest = Stapler.getCurrentRequest();
        this.adapter = adapter;
        this.baseList = baseList;
        this.baseUrl = Functions.getNearestAncestorUrl(currentRequest,owner);
        this.owner = owner;
        this.newerThan = getPagingParam(currentRequest, "newer-than");
        this.olderThan = getPagingParam(currentRequest, "older-than");
        this.searchString = currentRequest.getParameter("search");;
    }

    /**
     * Title of the widget.
     */
    public String getDisplayName() {
        return Messages.BuildHistoryWidget_DisplayName();
    }

    @Override
    public String getUrlName() {
        return "buildHistory";
    }

    public String getFirstTransientBuildKey() {
        return firstTransientBuildKey;
    }

    private Iterable<HistoryPageEntry<T>> updateFirstTransientBuildKey(Iterable<HistoryPageEntry<T>> source) {
        String key=null;
        for (HistoryPageEntry<T> t : source) {
            if(adapter.isBuilding(t.getEntry())) {
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
        if(trimmed) {
            List<HistoryPageEntry<T>> pageEntries = toPageEntries(baseList);
            if(pageEntries.size() > THRESHOLD) {
                return updateFirstTransientBuildKey(pageEntries.subList(0,THRESHOLD));
            } else {
                trimmed=false;
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
            return Collections.EMPTY_LIST;
        }

        List<HistoryPageEntry<T>> pageEntries = new ArrayList<HistoryPageEntry<T>>();
        while (iterator.hasNext()) {
	        pageEntries.add(new HistoryPageEntry<T>(iterator.next()));
        }

	return pageEntries;
    }

    /**
     * Render a "page" of records.
     */
    public HistoryPageFilter getPage() {
        HistoryPageFilter<T> historyPageFilter = newPageFilter();

        historyPageFilter.add(IteratorUtils.toList(baseList.iterator()));
        historyPageFilter.widget = this;
        return historyPageFilter;
    }

    protected HistoryPageFilter<T> newPageFilter() {
        HistoryPageFilter<T> historyPageFilter = new HistoryPageFilter<T>(THRESHOLD);

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
    public void doAjax( StaplerRequest req, StaplerResponse rsp,
		  @Header("n") String n ) throws IOException, ServletException {

        rsp.setContentType("text/html;charset=UTF-8");

        // pick up builds to send back
        List<T> items = new ArrayList<T>();

        if (n != null) {
            String nn=null; // we'll compute next n here

            // list up all builds >=n.
            for (T t : baseList) {
                if(adapter.compare(t,n)>=0) {
                    items.add(t);
                    if(adapter.isBuilding(t))
                    nn = adapter.getKey(t); // the next fetch should start from youngest build in progress
                } else
                    break;
            }

            if (nn==null) {
                if (items.isEmpty()) {
                    // nothing to report back. next fetch should retry the same 'n'
                    nn=n;
                } else {
                    // every record fetched this time is frozen. next fetch should start from the next build
                    nn=adapter.getNextKey(adapter.getKey(items.get(0)));
                }
            }

            baseList = items;

            rsp.setHeader("n",nn);
            firstTransientBuildKey = nn; // all builds >= nn should be marked transient
        }

        HistoryPageFilter page = getPage();
        updateFirstTransientBuildKey(page.runs);
        req.getView(page,"ajaxBuildHistory.jelly").forward(req,rsp);
    }

    static final int THRESHOLD = Integer.getInteger(HistoryWidget.class.getName()+".threshold",30);

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

    private Long getPagingParam(StaplerRequest currentRequest, String name) {
        String paramVal = currentRequest.getParameter(name);
        if (paramVal == null) {
            return null;
        }
        try {
            return new Long(paramVal);
        } catch (NumberFormatException nfe) {
            return null;
        }
    }
}
