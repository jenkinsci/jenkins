/*
 * The MIT License
 *
 * Copyright (c) 2026, Tim Jacomb
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

package jenkins.model.dashboard;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.Cause;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.User;
import hudson.scm.ChangeLogSet;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import jenkins.management.Badge;
import jenkins.model.Jenkins;
import jenkins.model.Tab;
import jenkins.scm.RunWithSCM;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.ResponseImpl;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.GET;

/**
 * Shows what is happening now: running builds, then queued builds, then recently finished builds.
 * Within each group, builds the current user started or contributed changes to come first.
 */
@Restricted(NoExternalUse.class)
public class RecentTab extends Tab {

    static final int LIMIT = 50;

    /**
     * How many of the most recently finished builds the user can see are ranked, so that their own builds are
     * found before the list is capped at {@link #LIMIT}.
     */
    static /* non-final for Groovy */ int FINISHED_CANDIDATES = 200;

    /**
     * How many queued builds show before the user expands the list.
     */
    static final int COLLAPSED_QUEUE_SIZE = 3;

    public RecentTab(@NonNull Dashboard dashboard) {
        super(dashboard);
    }

    @Override
    public String getIconFileName() {
        return "symbol-clock";
    }

    @Override
    public String getDisplayName() {
        return Messages.RecentTab_DisplayName();
    }

    @Override
    public String getUrlName() {
        return null;
    }

    @Override
    public Badge getBadge() {
        return badge(getRunningExecutors().size());
    }

    /**
     * The badge for a model, the same as {@link #getBadge()} without looking at the executors again.
     */
    @Restricted(NoExternalUse.class)
    public @CheckForNull Badge badgeFor(@NonNull Model model) {
        return badge(model.runningCount());
    }

    private static @CheckForNull Badge badge(int running) {
        if (running == 0) {
            return null;
        }
        return new Badge(Integer.toString(running), Messages.RecentTab_RunningBadge(running), Badge.Severity.INFO);
    }

    public int getCollapsedQueueSize() {
        return COLLAPSED_QUEUE_SIZE;
    }

    /**
     * How long ago a build started, such as "5 min". Unlike {@link Run#getTimestampString()}, this
     * leaves out the time the build spent waiting in the queue.
     */
    @Restricted(NoExternalUse.class)
    public @NonNull String startedAgo(@NonNull Run<?, ?> run) {
        return roughTimeSpan(System.currentTimeMillis() - run.getStartTimeInMillis());
    }

    /**
     * How long a running build is likely to take to finish, such as "5 min", or {@code null} if there is no
     * estimate, or the build has taken so much longer than estimated that it may be stuck. Uses the estimate the
     * executor read when the build started.
     */
    @Restricted(NoExternalUse.class)
    public @CheckForNull String remaining(@CheckForNull Executor executor) {
        if (executor == null || executor.isLikelyStuck()) {
            return null;
        }
        long remaining = executor.getEstimatedRemainingTimeMillis();
        return remaining > 0 ? roughTimeSpan(remaining) : null;
    }

    /**
     * How long a queued item has waited, such as "5 min".
     */
    @Restricted(NoExternalUse.class)
    public @NonNull String waitingFor(@NonNull Queue.Item item) {
        return roughTimeSpan(System.currentTimeMillis() - item.getInQueueSince());
    }

    /**
     * A time span like {@link Util#getTimeSpanString(long)}, in steps that grow with it: minutes up to ten minutes,
     * then five minutes, then hours, then days. A row then reads the same for a while, which matters most for the
     * many rows of builds that finished hours ago.
     */
    static @NonNull String roughTimeSpan(long duration) {
        long millis = Math.max(0, duration);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        if (minutes < 1) {
            return Messages.RecentTab_LessThanAMinute();
        }
        if (minutes < 10) {
            return hudson.Messages.Util_minute(minutes);
        }
        if (minutes < 60) {
            return hudson.Messages.Util_minute(minutes - minutes % 5);
        }
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        if (hours < 24) {
            return hudson.Messages.Util_hour(hours);
        }
        return hudson.Messages.Util_day(TimeUnit.MILLISECONDS.toDays(millis));
    }

    /**
     * How a build relates to the current user.
     */
    @Restricted(NoExternalUse.class)
    public enum Involvement {
        /** The user started it. */
        STARTED,
        /** It includes changes by the user. */
        CHANGES,
        NONE;

        public boolean isInvolved() {
            return this != NONE;
        }
    }

    @Restricted(NoExternalUse.class)
    public record Entry<T>(@NonNull T value, @NonNull Involvement involvement) {}

    /**
     * The groups shown on the tab, each capped at {@link #LIMIT} entries.
     *
     * @param runningCount the number of running builds the user can see, which may exceed the entries shown
     * @param queuedCount the number of queued builds the user can see, which may exceed the entries shown
     * @param executors the executor of each running build
     */
    @Restricted(NoExternalUse.class)
    public record Model(
            List<Entry<Run<?, ?>>> running,
            int runningCount,
            List<Entry<Queue.Item>> queued,
            int queuedCount,
            List<Entry<Run<?, ?>>> finished,
            Map<Run<?, ?>, Executor> executors) {
        public boolean isEmpty() {
            return running.isEmpty() && queued.isEmpty() && finished.isEmpty();
        }

        public @CheckForNull Executor executorOf(Run<?, ?> run) {
            return executors.get(run);
        }
    }

    /**
     * What the tab shows, for the current user.
     */
    public @NonNull Model getModel() {
        User user = User.current();
        String userId = user == null ? null : user.getId();
        Map<Run<?, ?>, Executor> running = getRunningExecutors();
        List<Queue.Item> queued = getQueued();
        return new Model(
                rank(sortedByStart(running.keySet()), run -> involvement(run, userId)),
                running.size(),
                rank(queued, item -> startedBy(item.getCauses(), userId) ? Involvement.STARTED : Involvement.NONE),
                queued.size(),
                rank(RecentRuns.get().getCompleted(FINISHED_CANDIDATES), run -> involvement(run, userId)),
                running);
    }

    /**
     * Whether the user expanded a collapsible section, whose rows past the first few are only rendered then.
     */
    @Restricted(NoExternalUse.class)
    public boolean isExpanded(@NonNull String section) {
        StaplerRequest2 request = Stapler.getCurrentRequest2();
        String expanded = request == null ? null : request.getParameter("expanded");
        return expanded != null && Arrays.asList(expanded.split(",")).contains(section);
    }

    /**
     * The rows of a section to render: all of them if it is expanded, otherwise the first few.
     */
    @Restricted(NoExternalUse.class)
    public @NonNull <T> List<T> shown(@NonNull String section, @NonNull List<T> entries) {
        return isExpanded(section) || entries.size() <= COLLAPSED_QUEUE_SIZE ? entries : entries.subList(0, COLLAPSED_QUEUE_SIZE);
    }

    /**
     * Queued items the current user can see. The queue checks the permission of each task, but not of the folders
     * containing it.
     */
    private static List<Queue.Item> getQueued() {
        List<Queue.Item> result = new ArrayList<>();
        for (Queue.Item item : Jenkins.get().getQueue().getItems()) {
            if (!(item.task.getOwnerTask() instanceof Item owner) || canSee(owner)) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Whether the current user can see an item and every folder containing it, as needed to open its page.
     * Not every item group checks the permission of the items it returns, so the item's own is checked too.
     */
    private static boolean canSee(Item item) {
        return item.hasPermission(Item.READ) && Jenkins.get().getItemByFullNameOrNull(item.getFullName()) != null;
    }

    private static List<Run<?, ?>> sortedByStart(Collection<Run<?, ?>> runs) {
        List<Run<?, ?>> result = new ArrayList<>(runs);
        result.sort(Comparator.comparingLong((Run<?, ?> r) -> r.getStartTimeInMillis()).reversed());
        return result;
    }

    /**
     * Maps each running build the current user can see to its executor. A Pipeline build can occupy
     * several executors; the one running the build itself is preferred over those running its
     * {@code node} blocks.
     */
    private static Map<Run<?, ?>, Executor> getRunningExecutors() {
        Map<Run<?, ?>, Executor> runs = new HashMap<>();
        for (Computer computer : Jenkins.get().getComputers()) {
            for (Executor executor : computer.getAllExecutors()) {
                Queue.Executable executable = executor.getCurrentExecutable();
                Run<?, ?> run = owningRun(executable);
                // An executor keeps a build for a while after it completes
                if (run != null
                        && run.isBuilding()
                        && (executable == run || !runs.containsKey(run))
                        && canSee(run.getParent())) {
                    runs.put(run, executor);
                }
            }
        }
        return runs;
    }

    /**
     * Finds the build an executable belongs to, so that, for example, a Pipeline {@code node} block maps to its build.
     * The depth limit guards against a cycle of parent executables.
     */
    private static @CheckForNull Run<?, ?> owningRun(@CheckForNull Queue.Executable executable) {
        Queue.Executable current = executable;
        for (int depth = 0; current != null && depth < 10; depth++) {
            if (current instanceof Run<?, ?> run) {
                return run;
            }
            try {
                current = current.getParentExecutable();
            } catch (RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Puts the entries involving the user first, keeping the original order otherwise, then caps the list.
     */
    private static <T> List<Entry<T>> rank(List<T> values, Function<T, Involvement> involvement) {
        List<Entry<T>> entries = new ArrayList<>(values.size());
        for (T value : values) {
            entries.add(new Entry<>(value, involvement.apply(value)));
        }
        // Stable, so the original order is kept within each group
        entries.sort(Comparator.comparing(e -> !e.involvement().isInvolved()));
        return entries.size() > LIMIT ? new ArrayList<>(entries.subList(0, LIMIT)) : entries;
    }

    static @NonNull Involvement involvement(@NonNull Run<?, ?> run, @CheckForNull String userId) {
        if (userId == null) {
            return Involvement.NONE;
        }
        if (startedBy(run.getCauses(), userId)) {
            return Involvement.STARTED;
        }
        if (run instanceof RunWithSCM<?, ?> withScm) {
            try {
                // The culprits of a completed build are stored with it and include the authors of its changes,
                // so they rule out most builds without reading the changes
                if (!run.isBuilding() && !withScm.shouldCalculateCulprits() && !containsUser(withScm.getCulpritIds(), userId)) {
                    return Involvement.NONE;
                }
                // The build's own changes, unlike culprits, which include changes from earlier failed builds
                for (ChangeLogSet<? extends ChangeLogSet.Entry> changeSet : withScm.getChangeSets()) {
                    for (ChangeLogSet.Entry entry : changeSet) {
                        if (sameUser(entry.getAuthor().getId(), userId)) {
                            return Involvement.CHANGES;
                        }
                    }
                }
            } catch (RuntimeException e) {
                // A broken SCM integration must not break the dashboard
                return Involvement.NONE;
            }
        }
        return Involvement.NONE;
    }

    private static boolean startedBy(List<Cause> causes, @CheckForNull String userId) {
        return userId != null && causes.stream()
                .anyMatch(cause -> cause instanceof Cause.UserIdCause userIdCause && sameUser(userIdCause.getUserId(), userId));
    }

    /**
     * Whether two user IDs name the same user. A cause records the name the user signed in with, which can differ
     * in case from their ID with a security realm that ignores case.
     */
    private static boolean sameUser(@CheckForNull String a, @NonNull String b) {
        return a != null && User.idStrategy().equals(a, b);
    }

    private static boolean containsUser(@CheckForNull Collection<String> ids, @NonNull String userId) {
        return ids != null && ids.stream().anyMatch(id -> sameUser(id, userId));
    }

    /**
     * Whether an {@code If-None-Match} header names an entity tag, comparing weakly as for a {@code GET}: a list
     * of tags matches if any of them does, a {@code W/} prefix is ignored, and {@code *} matches anything.
     */
    static boolean matchesAny(@CheckForNull String ifNoneMatch, @NonNull String etag) {
        if (ifNoneMatch == null) {
            return false;
        }
        String tag = etag.startsWith("W/") ? etag.substring(2) : etag;
        for (String candidate : ifNoneMatch.split(",")) {
            String c = candidate.trim();
            if (c.equals("*")) {
                return true;
            }
            if (c.startsWith("W/")) {
                c = c.substring(2);
            }
            if (c.equals(tag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Renders the groups as an HTML fragment, for refreshing the page. The response has an {@code ETag} from its
     * content, and a request whose {@code If-None-Match} matches it gets an empty "304 Not Modified" response
     * rather than the same rows again.
     */
    @GET
    public void doRows(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException, ServletException {
        BufferedResponse buffered = new BufferedResponse(rsp);
        req.getView(this, "rows.jelly").forward(req, new ResponseImpl(Stapler.getCurrent(), buffered));
        byte[] body = buffered.toByteArray();
        String etag = '"' + Util.getDigestOf(new ByteArrayInputStream(body)) + '"';
        rsp.setHeader("ETag", etag);
        if (matchesAny(req.getHeader("If-None-Match"), etag)) {
            rsp.setStatus(StaplerResponse2.SC_NOT_MODIFIED);
            return;
        }
        rsp.setContentType("text/html;charset=UTF-8");
        rsp.setContentLength(body.length);
        rsp.getOutputStream().write(body);
    }

    /**
     * Keeps what is written to a response, so that it can be sent or not once it is complete.
     */
    private static final class BufferedResponse extends HttpServletResponseWrapper {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private PrintWriter writer;

        BufferedResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() {
            return new ServletOutputStream() {
                @Override
                public void write(int b) {
                    bytes.write(b);
                }

                @Override
                public void write(byte[] b, int off, int len) {
                    bytes.write(b, off, len);
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener writeListener) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public PrintWriter getWriter() {
            if (writer == null) {
                writer = new PrintWriter(new OutputStreamWriter(bytes, StandardCharsets.UTF_8));
            }
            return writer;
        }

        byte[] toByteArray() {
            if (writer != null) {
                writer.flush();
            }
            return bytes.toByteArray();
        }
    }
}
