/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Michael B. Donohue, Seiji Sogabe
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import hudson.console.ModelHyperlinkNote;
import hudson.diagnosis.OldDataMonitor;
import hudson.util.XStream2;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import hudson.Util;
import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Cause object base class.  This class hierarchy is used to keep track of why
 * a given build was started. This object encapsulates the UI rendering of the cause,
 * as well as providing more useful information in respective subypes.
 *
 * The Cause object is connected to a build via the {@link CauseAction} object.
 *
 * <h2>Views</h2>
 * <dl>
 * <dt>description.jelly
 * <dd>Renders the cause to HTML. By default, it puts the short description.
 * </dl>
 *
 * @author Michael Donohue
 * @see Run#getCauses()
 * @see Queue.Item#getCauses()
 */
@ExportedBean
public abstract class Cause {
    /**
     * One-line human-readable text of the cause.
     *
     * <p>
     * By default, this method is used to render HTML as well.
     */
    @Exported(visibility=3)
    public abstract String getShortDescription();

    /**
     * Called when the cause is registered.
     * @since 1.568
     */
    public void onAddedTo(@Nonnull Run build) {
        if (build instanceof AbstractBuild) {
            onAddedTo((AbstractBuild) build);
        }
    }

    @Deprecated
    public void onAddedTo(AbstractBuild build) {
        if (Util.isOverridden(Cause.class, getClass(), "onAddedTo", Run.class)) {
            onAddedTo((Run) build);
        }
    }

    /**
     * Called when a build is loaded from disk.
     * Useful in case the cause needs to keep a build reference;
     * this ought to be {@code transient}.
     * @since 1.568
     */
    public void onLoad(@Nonnull Run<?,?> build) {
        if (build instanceof AbstractBuild) {
            onLoad((AbstractBuild) build);
        }
    }

    void onLoad(@Nonnull Job<?,?> job, int buildNumber) {
        Run<?,?> build = job.getBuildByNumber(buildNumber);
        if (build != null) {
            onLoad(build);
        }
    }

    @Deprecated
    public void onLoad(AbstractBuild<?,?> build) {
        if (Util.isOverridden(Cause.class, getClass(), "onLoad", Run.class)) {
            onLoad((Run) build);
        }
    }

    /**
     * Report a line to the listener about this cause.
     * @since 1.362
     */
    public void print(TaskListener listener) {
        listener.getLogger().println(getShortDescription());
    }

    /**
     * Fall back implementation when no other type is available.
     * @deprecated since 2009-02-08
     */
    @Deprecated
    public static class LegacyCodeCause extends Cause {
        private StackTraceElement [] stackTrace;
        public LegacyCodeCause() {
            stackTrace = new Exception().getStackTrace();
        }

        @Override
        public String getShortDescription() {
            return Messages.Cause_LegacyCodeCause_ShortDescription();
        }
    }

    /**
     * A build is triggered by another build (AKA upstream build.)
     */
    public static class UpstreamCause extends Cause {

        /**
         * Maximum depth of transitive upstream causes we want to record.
         */
        private static final int MAX_DEPTH = 10;
        /**
         * Maximum number of transitive upstream causes we want to record.
         */
        private static final int MAX_LEAF = 25;
        private String upstreamProject, upstreamUrl;
        private int upstreamBuild;
        /**
         * @deprecated since 2009-02-28
         */
        @Deprecated
        private transient Cause upstreamCause;
        private @Nonnull List<Cause> upstreamCauses;

        /**
         * @deprecated since 2009-02-28
         */
        // for backward bytecode compatibility
        @Deprecated
        public UpstreamCause(AbstractBuild<?,?> up) {
            this((Run<?,?>)up);
        }

        public UpstreamCause(Run<?, ?> up) {
            upstreamBuild = up.getNumber();
            upstreamProject = up.getParent().getFullName();
            upstreamUrl = up.getParent().getUrl();
            upstreamCauses = new ArrayList<Cause>();
            Set<String> traversed = new HashSet<String>();
            for (Cause c : up.getCauses()) {
                upstreamCauses.add(trim(c, MAX_DEPTH, traversed));
            }
        }

        private UpstreamCause(String upstreamProject, int upstreamBuild, String upstreamUrl, @Nonnull List<Cause> upstreamCauses) {
            this.upstreamProject = upstreamProject;
            this.upstreamBuild = upstreamBuild;
            this.upstreamUrl = upstreamUrl;
            this.upstreamCauses = upstreamCauses;
        }

        @Override
        public void onLoad(@Nonnull Job<?,?> _job, int _buildNumber) {
            Item i = Jenkins.getInstance().getItemByFullName(this.upstreamProject);
            if (i == null || !(i instanceof Job)) {
                // cannot initialize upstream causes
                return;
            }

            Job j = (Job)i;
            for (Cause c : this.upstreamCauses) {
                c.onLoad(j, upstreamBuild);
            }
        }

        /**
         * @since 1.515
         */
        @Override
        public boolean equals(Object rhs) {

            if (this == rhs) return true;

            if (!(rhs instanceof UpstreamCause)) return false;

            final UpstreamCause o = (UpstreamCause) rhs;

            return Objects.equals(upstreamBuild, o.upstreamBuild) &&
                    Objects.equals(upstreamCauses, o.upstreamCauses) &&
                    Objects.equals(upstreamUrl, o.upstreamUrl) &&
                    Objects.equals(upstreamProject, o.upstreamProject);
        }

        /**
         * @since 1.515
         */
        @Override
        public int hashCode() {
            return Objects.hash(upstreamCauses, upstreamBuild, upstreamUrl, upstreamProject);
        }

        private @Nonnull Cause trim(@Nonnull Cause c, int depth, Set<String> traversed) {
            if (!(c instanceof UpstreamCause)) {
                return c;
            }
            UpstreamCause uc = (UpstreamCause) c;
            List<Cause> cs = new ArrayList<Cause>();
            if (depth > 0) {
                if (traversed.add(uc.upstreamUrl + uc.upstreamBuild)) {
                    for (Cause c2 : uc.upstreamCauses) {
                        cs.add(trim(c2, depth - 1, traversed));
                    }
                }
            } else if (traversed.size() < MAX_LEAF) {
                cs.add(new DeeplyNestedUpstreamCause());
            }
            return new UpstreamCause(uc.upstreamProject, uc.upstreamBuild, uc.upstreamUrl, cs);
        }

        /**
         * Returns true if this cause points to a build in the specified job.
         */
        public boolean pointsTo(Job<?,?> j) {
            return j.getFullName().equals(upstreamProject);
        }

        /**
         * Returns true if this cause points to the specified build.
         */
        public boolean pointsTo(Run<?,?> r) {
            return r.getNumber()==upstreamBuild && pointsTo(r.getParent());
        }

        @Exported(visibility=3)
        public String getUpstreamProject() {
            return upstreamProject;
        }

        @Exported(visibility=3)
        public int getUpstreamBuild() {
            return upstreamBuild;
        }

        /**
         * @since 1.505
         */
        public @CheckForNull Run<?,?> getUpstreamRun() {
            Job<?,?> job = Jenkins.getInstance().getItemByFullName(upstreamProject, Job.class);
            return job != null ? job.getBuildByNumber(upstreamBuild) : null;
        }

        @Exported(visibility=3)
        public String getUpstreamUrl() {
            return upstreamUrl;
        }

        public List<Cause> getUpstreamCauses() {
            return upstreamCauses;
        }
        
        @Override
        public String getShortDescription() {
            return Messages.Cause_UpstreamCause_ShortDescription(upstreamProject, upstreamBuild);
        }

        @Override
        public void print(TaskListener listener) {
            print(listener, 0);
        }

        private void indent(TaskListener listener, int depth) {
            for (int i = 0; i < depth; i++) {
                listener.getLogger().print(' ');
            }
        }

        private void print(TaskListener listener, int depth) {
            indent(listener, depth);
            listener.getLogger().println(
                Messages.Cause_UpstreamCause_ShortDescription(
                    ModelHyperlinkNote.encodeTo('/' + upstreamUrl, upstreamProject),
                    ModelHyperlinkNote.encodeTo('/'+upstreamUrl+upstreamBuild, Integer.toString(upstreamBuild)))
            );
            if (upstreamCauses != null && !upstreamCauses.isEmpty()) {
                indent(listener, depth);
                listener.getLogger().println(Messages.Cause_UpstreamCause_CausedBy());
                for (Cause cause : upstreamCauses) {
                    if (cause instanceof UpstreamCause) {
                        ((UpstreamCause) cause).print(listener, depth + 1);
                    } else {
                        indent(listener, depth + 1);
                        cause.print(listener);
                    }
                }
            }
        }

        @Override public String toString() {
            return upstreamUrl + upstreamBuild + upstreamCauses;
        }

        public static class ConverterImpl extends XStream2.PassthruConverter<UpstreamCause> {
            public ConverterImpl(XStream2 xstream) { super(xstream); }
            @Override protected void callback(UpstreamCause uc, UnmarshallingContext context) {
                if (uc.upstreamCause != null) {
                    if (uc.upstreamCauses == null) uc.upstreamCauses = new ArrayList<Cause>();
                    uc.upstreamCauses.add(uc.upstreamCause);
                    uc.upstreamCause = null;
                    OldDataMonitor.report(context, "1.288");
                }
            }
        }

        public static class DeeplyNestedUpstreamCause extends Cause {
            @Override public String getShortDescription() {
                return "(deeply nested causes)";
            }
            @Override public String toString() {
                return "JENKINS-14814";
            }
            @Override public void onLoad(@Nonnull Job<?,?> _job, int _buildNumber) {}
        }

    }

    /**
     * A build is started by an user action.
     *
     * @deprecated 1.428
     *   use {@link UserIdCause}
     */
    @Deprecated
    public static class UserCause extends Cause {
        private String authenticationName;
        public UserCause() {
            this.authenticationName = Jenkins.getAuthentication().getName();
        }

        @Exported(visibility=3)
        public String getUserName() {
        	return User.get(authenticationName).getDisplayName();
        }

        @Override
        public String getShortDescription() {
            return Messages.Cause_UserCause_ShortDescription(authenticationName);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof UserCause && Arrays.equals(new Object[] {authenticationName},
                    new Object[] {((UserCause)o).authenticationName});
        }

        @Override
        public int hashCode() {
            return 295 + (this.authenticationName != null ? this.authenticationName.hashCode() : 0);
        }
    }

    /**
     * A build is started by an user action.
     *
     * @since 1.427
     */
    public static class UserIdCause extends Cause {

        private String userId;

        public UserIdCause() {
            User user = User.current();
            this.userId = (user == null) ? null : user.getId();
        }

        @Exported(visibility = 3)
        public String getUserId() {
            return userId;
        }

        @Exported(visibility = 3)
        public String getUserName() {
            return userId == null ? "anonymous" : User.get(userId).getDisplayName();
        }

        @Override
        public String getShortDescription() {
            return Messages.Cause_UserIdCause_ShortDescription(getUserName());
        }

        @Override
        public void print(TaskListener listener) {
            listener.getLogger().println(Messages.Cause_UserIdCause_ShortDescription(
                    // TODO better to use ModelHyperlinkNote.encodeTo(User), or User.getUrl, since it handles URL escaping
                    ModelHyperlinkNote.encodeTo("/user/"+getUserId(), getUserName())));
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof UserIdCause && Objects.equals(userId, ((UserIdCause) o).userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId);
        }
    }

    public static class RemoteCause extends Cause {
        private String addr;
        private String note;

        public RemoteCause(String host, String note) {
            this.addr = host;
            this.note = note;
        }

        @Override
        public String getShortDescription() {
            if(note != null) {
                try {
                    return Messages.Cause_RemoteCause_ShortDescriptionWithNote(addr, Jenkins.getInstance().getMarkupFormatter().translate(note));
                } catch (IOException x) {
                    // ignore
                }
            }
            return Messages.Cause_RemoteCause_ShortDescription(addr);
        }
        
        @Exported(visibility = 3)
        public String getAddr() {
            return addr;
        }
        
        @Exported(visibility = 3)
        public String getNote() {
            return note;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof RemoteCause && Objects.equals(addr, ((RemoteCause) o).addr) && Objects.equals(note, ((RemoteCause) o).note);
        }

        @Override
        public int hashCode() {
            return Objects.hash(addr, note);
        }
    }
}
