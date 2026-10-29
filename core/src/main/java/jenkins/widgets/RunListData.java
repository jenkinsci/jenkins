/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

package jenkins.widgets;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Run;
import hudson.util.HttpResponses;
import hudson.util.RunList;
import java.util.concurrent.TimeUnit;
import jenkins.util.SystemProperties;
import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;

/**
 * Serves a page of some information from a {@link RunList} as JSON.
 * <p>
 * Runs are expected newest first, ordered by {@link Run#getTimeInMillis()}, as {@link RunList} provides them.
 * A page holds runs strictly older than the {@code olderThan} cursor; the response's {@code next} is the cursor
 * for the following page, or {@code null} once there are no more runs.
 * Runs sharing a timestamp are never split across pages, so the cursor is an exact boundary.
 * <p>
 * Filtered lists (such as the builds of a user) may need to scan many runs to fill a page,
 * so a page may be cut short once {@link #SCAN_BUDGET_MS} has elapsed, in which case {@code partial} is set
 * and the client should request the next page straight away.
 */
@Restricted(NoExternalUse.class)
public abstract class RunListData {

    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* non-final for script console */ int PAGE_SIZE = SystemProperties.getInteger(RunListData.class.getName() + ".pageSize", 50);

    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "for script console")
    public static /* non-final for script console */ long SCAN_BUDGET_MS = SystemProperties.getLong(RunListData.class.getName() + ".scanBudgetMs", 1000L);

    /**
     * Serves one page of runs.
     * @param builds runs, newest first
     * @param olderThan cursor from a previous page's {@code next}, or {@code null} for the first page
     */
    public HttpResponse serve(Iterable<? extends Run<?, ?>> builds, @CheckForNull String olderThan) {
        long cursor;
        if (olderThan == null || olderThan.isEmpty()) {
            cursor = Long.MAX_VALUE;
        } else {
            try {
                cursor = Long.parseLong(olderThan);
            } catch (NumberFormatException e) {
                return HttpResponses.error(400, "Invalid olderThan: " + olderThan);
            }
        }
        return HttpResponses.okJSON(page(builds, cursor));
    }

    JSONObject page(Iterable<? extends Run<?, ?>> builds, long cursor) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SCAN_BUDGET_MS);
        JSONArray results = new JSONArray();
        Long groupTime = null;
        Long next = null;
        boolean partial = false;
        for (Run<?, ?> build : builds) {
            long time = build.getTimeInMillis();
            if (time >= cursor) {
                continue;
            }
            if (groupTime != null && time != groupTime) {
                boolean full = results.size() >= PAGE_SIZE;
                if (full || System.nanoTime() - deadline >= 0) {
                    next = groupTime;
                    partial = !full;
                    break;
                }
            }
            JSONObject element = new JSONObject();
            calculate(build, element);
            results.add(element);
            groupTime = time;
        }
        JSONObject page = new JSONObject();
        page.put("builds", results);
        page.put("next", next != null ? next : JSONNull.getInstance());
        page.put("partial", partial);
        return page;
    }

    protected abstract void calculate(Run<?, ?> build, JSONObject element);
}
