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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.AbstractItem;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSON;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.RequestImpl;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.TokenList;
import org.kohsuke.stapler.bind.Bound;
import org.kohsuke.stapler.bind.BoundObjectTable;
import org.kohsuke.stapler.bind.JavaScriptMethod;
import org.kohsuke.stapler.jelly.BindTag;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

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
 * {@code design-library} demonstrates all this.
 * @since 1.484
 */
public abstract class ProgressiveRendering {

    private static final Logger LOG = Logger.getLogger(ProgressiveRendering.class.getName());
    /** May be set to a number of milliseconds to sleep in {@link #canceled}, useful for watching what are normally fast computations. */
    private static final Long DEBUG_SLEEP = SystemProperties.getLong("jenkins.util.ProgressiveRendering.DEBUG_SLEEP");
    private static final int CANCELED = -1;
    private static final int ERROR = -2;

    private double status = 0;
    private long lastNewsTime;
    private final SecurityContext securityContext;
    private final RequestImpl request;
    /** just for logging */
    private final String uri;
    private long start;
    private BoundObjectTable.Table boundObjectTable;
    /** Unfortunately we cannot get the {@link Bound} that was created for us; it is thrown out by {@link BindTag}. */
    private String boundId;

    /** Constructor for subclasses. */
    protected ProgressiveRendering() {
        securityContext = SecurityContextHolder.getContext();
        request = createMockRequest();
        uri = request.getRequestURI();
    }

    /**
     * For internal use.
     */
    @JavaScriptMethod public final void start() {
        Ancestor ancestor = Stapler.getCurrentRequest2().findAncestor(BoundObjectTable.class);
        if (ancestor == null) {
            throw new IllegalStateException("no BoundObjectTable");
        }
        boundObjectTable = ((BoundObjectTable) ancestor.getObject()).getTable();
        boundId = ancestor.getNextToken(0);
        LOG.log(Level.FINE, "starting rendering {0} at {1}", new Object[] {uri, boundId});
        final ExecutorService executorService = executorService();
        executorService.submit(() -> {
            lastNewsTime = start = System.currentTimeMillis();
            setCurrentRequest(request);
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
                setCurrentRequest(null);
                LOG.log(Level.FINE, "{0} finished in {1}msec with status {2}", new Object[] {uri, System.currentTimeMillis() - start, status});
            }
            if (executorService instanceof ScheduledExecutorService) {
                ((ScheduledExecutorService) executorService).schedule(() -> {
                    LOG.log(Level.FINE, "some time has elapsed since {0} finished, so releasing", boundId);
                    boundObjectTable.release(boundId);
                }, timeout() /* add some grace period for browser/network overhead */ * 2, TimeUnit.MILLISECONDS);
            }
        });
    }

    /**
     * Copies important fields from the current HTTP request and makes them available during {@link #compute}.
     * This is necessary because some model methods such as {@link AbstractItem#getUrl} behave differently when called from a request.
     */
    @java.lang.SuppressWarnings({"rawtypes", "unchecked"}) // public RequestImpl ctor requires List<AncestorImpl> yet AncestorImpl is not public! API design flaw
    private static RequestImpl createMockRequest() {
        RequestImpl currentRequest = (RequestImpl) Stapler.getCurrentRequest2();
        HttpServletRequest original = (HttpServletRequest) currentRequest.getRequest();
        final Map<String, Object> getters = new HashMap<>();
        for (Method method : HttpServletRequest.class.getMethods()) {
            String m = method.getName();
            if ((m.startsWith("get") || m.startsWith("is")) && method.getParameterTypes().length == 0) {
                Class<?> type = method.getReturnType();
                // TODO could add other types which are known to be safe to copy: Cookie[], Principal, HttpSession, etc.
                if (type.isPrimitive() || type == String.class || type == Locale.class) {
                    try {
                        getters.put(m, method.invoke(original));
                    } catch (Exception x) {
                        LOG.log(Level.WARNING, "cannot mock Stapler request " + method, x);
                    }
                }
            }
        }
        List/*<AncestorImpl>*/ ancestors = currentRequest.ancestors;
        LOG.log(Level.FINER, "mocking ancestors {0} using {1}", new Object[] {ancestors, getters});
        TokenList tokens = currentRequest.tokens;
        return new RequestImpl(Stapler.getCurrent(), (HttpServletRequest) Proxy.newProxyInstance(ProgressiveRendering.class.getClassLoader(), new Class<?>[] {HttpServletRequest.class}, (proxy, method, args) -> {
            String m = method.getName();
            if (getters.containsKey(m)) {
                return getters.get(m);
            } else { // TODO implement other methods as needed
                throw new UnsupportedOperationException(m);
            }
        }), ancestors, tokens);
    }

    @java.lang.SuppressWarnings("unchecked")
    private static void setCurrentRequest(RequestImpl request) {
        try {
            Field field = Stapler.class.getDeclaredField("CURRENT_REQUEST");
            field.setAccessible(true);
            ((ThreadLocal<RequestImpl>) field.get(null)).set(request);
        } catch (Exception x) {
            LOG.log(Level.WARNING, "cannot mock Stapler request", x);
        }
    }

    /**
     * Actually do the work.
     * <p>The security context will be that in effect when the web request was made.
     * {@link Stapler#getCurrentRequest2} will also be similar to that in effect when the web request was made;
     * at least, {@link Ancestor}s and basic request properties (URI, locale, and so on) will be available.
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
    protected abstract @NonNull JSON data();

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
        if (DEBUG_SLEEP != null) {
            try {
                Thread.sleep(DEBUG_SLEEP);
            } catch (InterruptedException x) { }
        }
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
        Object statusJSON = status == 1 ? "done" : status == CANCELED ? "canceled" : status == ERROR ? "error" : status;
        r.put("status", statusJSON);
        if (statusJSON instanceof String) { // somehow completed
            LOG.log(Level.FINE, "finished in news so releasing {0}", boundId);
            boundObjectTable.release(boundId);
        }
        lastNewsTime = System.currentTimeMillis();
        LOG.log(Level.FINER, "news from {0}", uri);
        return r;
    }

    /**
     * May be overridden to provide an alternate executor service.
     * @return by default, {@link Timer#get}
     */
    protected ExecutorService executorService() {
        return Timer.get();
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
