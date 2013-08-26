/*
 * The MIT License
 *
 * Copyright 2012 Jesse Glick.
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

package jenkins.util;

import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import hudson.model.Computer;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import net.sf.json.JSON;
import net.sf.json.JSONObject;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.bind.JavaScriptMethod;

/**
 * A helper thread which does some computation in the background and displays incremental results using JavaScript.
 * This is appropriate when the computation may be slow—too slow to do synchronously within the initial HTTP request—and has no side effects
 * (since it may be canceled if the user simply browses to another page while it is running).
 * <ol>
 * <li>Write a {@code <script>} section defining {@code function display(data)}.
 *     (Call {@code ts_refresh($('someid'))} if using a {@code sortable} table.)
 * <li>Use {@code <l:progressiveRendering handler="${it.something()}" callback="display"/>} from your
 *     Jelly page to display a progress bar and initialize JavaScript infrastructure.
 *     (The callback attribute can take arbitrary JavaScript expression to be evaluated in the browser
 *     so long as it produces a function object.)
 * <li>Implement {@code something()} to create an instance of your subclass of {@code ProgressiveRendering}.
 * <li>Perform your work in {@link #compute}.
 * <li>Periodically check {@link #canceled}.
 * <li>As results become available, call {@link #progress}.
 * <li>Make {@link #data} produce whatever JSON you want to send to the page to be displayed.
 * </ol>
 * {@code ui-samples-plugin} demonstrates all this.
 * @since 1.484
 */
public abstract class ProgressiveRendering {

    private static final Logger LOG = Logger.getLogger(ProgressiveRendering.class.getName());
    private static final int CANCELED = -1;
    private static final int ERROR = -2;

    private double status = 0;
    private long lastNewsTime;
    /** just for logging */
    private final String uri;
    private long start;

    /** Constructor for subclasses. */
    protected ProgressiveRendering() {
        StaplerRequest currentRequest = Stapler.getCurrentRequest();
        uri = currentRequest != null ? currentRequest.getRequestURI() : "?";
    }

    /**
     * For internal use.
     */
    @SuppressWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    public final void start() {
        final SecurityContext securityContext = SecurityContextHolder.getContext();
        executorService().submit(new Runnable() {
            public void run() {
                lastNewsTime = start = System.currentTimeMillis();
                SecurityContext orig = SecurityContextHolder.getContext();
                try {
                    SecurityContextHolder.setContext(securityContext);
                    compute();
                    if (status != CANCELED && status != ERROR) {
                        status = 1;
                    }
                } catch (Exception x) {
                    LOG.log(Level.WARNING, "failed to compute " + uri, x);
                    status = ERROR;
                } finally {
                    SecurityContextHolder.setContext(orig);
                    LOG.log(Level.FINE, "{0} finished in {1}msec with status {2}", new Object[] {uri, System.currentTimeMillis() - start, status});
                }
            }
        });
    }

    /**
     * Actually do the work.
     * <p>The security context will be that in effect when the web request was made.
     * @throws Exception whenever you like; the progress bar will indicate that an error occurred but details go to the log only
     */
    protected abstract void compute() throws Exception;

    /**
     * Provide current data to the web page for display.
     * <p>While this could be an aggregate of everything that has been computed so far,
     * more likely you want to supply only that data that is new since the last call
     * (maybe just {@code {}} or {@code []}),
     * so that the page can incrementally update bits of HTML rather than refreshing everything.
     * <p>You may want to make your implementation {@code synchronized}, so that it
     * can track what was sent on a previous call, in which case any code running in
     * {@link #compute} which modifies these fields should also <em>temporarily</em> be synchronized
     * on the same monitor such as {@code this}.
     * @return any JSON data you like
     */
    protected abstract @Nonnull JSON data();

    /**
     * Indicate what portion of the work has been done.
     * (Once {@link #compute} returns, the work is assumed to be complete regardless of this method.)
     * @param completedFraction estimated portion of work now done, from 0 (~ 0%) to 1 (~ 100%)
     */
    protected final void progress(double completedFraction) {
        if (completedFraction < 0 || completedFraction > 1) {
            throw new IllegalArgumentException(completedFraction + " should be in [0,1]");
        }
        status = completedFraction;
    }

    /**
     * Checks whether the task has been canceled.
     * If the rendering page fails to send a heartbeat within a certain amount of time,
     * the user is assumed to have moved on.
     * Therefore {@link #compute} should periodically say:
     * {@code if (canceled()) return;}
     * @return true if user seems to have abandoned us, false if we should still run
     */
    protected final boolean canceled() {
        if (status == ERROR) {
            return true; // recent call to data() failed
        }
        long now = System.currentTimeMillis();
        long elapsed = now - lastNewsTime;
        if (elapsed > timeout()) {
            status = CANCELED;
            LOG.log(Level.FINE, "{0} canceled due to {1}msec inactivity after {2}msec", new Object[] {uri, elapsed, now - start});
            return true;
        } else {
            return false;
        }
    }

    /**
     * For internal use.
     */
    @JavaScriptMethod public final JSONObject news() {
        lastNewsTime = System.currentTimeMillis();
        JSONObject r = new JSONObject();
        try {
            r.put("data", data());
        } catch (RuntimeException x) {
            LOG.log(Level.WARNING, "failed to update " + uri, x);
            status = ERROR;
        }
        r.put("status", status == 1 ? "done" : status == CANCELED ? "canceled" : status == ERROR ? "error" : status);
        lastNewsTime = System.currentTimeMillis();
        LOG.log(Level.FINER, "news from {0}", uri);
        return r;
    }

    /**
     * May be overridden to provide an alternate executor service.
     * @return by default, {@link Computer#threadPoolForRemoting}
     */
    protected ExecutorService executorService() {
        return Computer.threadPoolForRemoting;
    }

    /**
     * May be overridden to control the inactivity timeout.
     * If no request from the browser is received within this time,
     * the next call to {@link #canceled} will be true.
     * @return timeout in milliseconds; by default, 15000 (~ 15 seconds)
     */
    protected long timeout() {
        return 15000;
    }

}
