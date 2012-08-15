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
     * Called when the cause is registered to {@link AbstractBuild}.
     *
     * @param build
     *      never null
     * @since 1.376
     */
    public void onAddedTo(AbstractBuild build) {}

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
     * A build is triggered by the completion of another build (AKA upstream build.)
     */
    public static class UpstreamCause extends Cause {

        /**
         * Maximum depth of transitive upstream causes we want to record.
         */
        private static final int MAX_DEPTH = 10;
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
        public UpstreamCause(AbstractBuild<?,?> up) {
            this((Run<?,?>)up);
        }

        public UpstreamCause(Run<?, ?> up) {
            upstreamBuild = up.getNumber();
            upstreamProject = up.getParent().getFullName();
            upstreamUrl = up.getParent().getUrl();
            upstreamCauses = new ArrayList<Cause>();
            for (Cause c : up.getCauses()) {
                upstreamCauses.add(trim(c, MAX_DEPTH));
            }
        }

        private UpstreamCause(String upstreamProject, int upstreamBuild, String upstreamUrl, @Nonnull List<Cause> upstreamCauses) {
            this.upstreamProject = upstreamProject;
            this.upstreamBuild = upstreamBuild;
            this.upstreamUrl = upstreamUrl;
            this.upstreamCauses = upstreamCauses;
        }

        private @Nonnull Cause trim(@Nonnull Cause c, int depth) {
            if (!(c instanceof UpstreamCause)) {
                return c;
            }
            UpstreamCause uc = (UpstreamCause) c;
            List<Cause> cs = new ArrayList<Cause>();
            if (depth > 0) {
                for (Cause c2 : uc.upstreamCauses) {
                    cs.add(trim(c2, depth - 1));
                }
            } else {
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

        @Exported(visibility=3)
        public String getUpstreamUrl() {
            return upstreamUrl;
        }

        @Override
        public String getShortDescription() {
            return Messages.Cause_UpstreamCause_ShortDescription(upstreamProject, upstreamBuild);
        }

        @Override
        public void print(TaskListener listener) {
            listener.getLogger().println(
                Messages.Cause_UpstreamCause_ShortDescription(
                    ModelHyperlinkNote.encodeTo('/' + upstreamUrl, upstreamProject),
                    ModelHyperlinkNote.encodeTo('/'+upstreamUrl+upstreamBuild, Integer.toString(upstreamBuild)))
            );
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
        }

    }

    /**
     * A build is started by an user action.
     *
     * @deprecated 1.428
     *   use {@link UserIdCause}
     */
    public static class UserCause extends Cause {
        private String authenticationName;
        public UserCause() {
            this.authenticationName = Jenkins.getAuthentication().getName();
        }

        @Exported(visibility=3)
        public String getUserName() {
        	User u = User.get(authenticationName, false);
            return u != null ? u.getDisplayName() : authenticationName;
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
            String userName = "anonymous";
            if (userId != null) {
                User user = User.get(userId, false);
                if (user != null)
                    userName = user.getDisplayName();
            }
            return userName;
        }

        @Override
        public String getShortDescription() {
            return Messages.Cause_UserIdCause_ShortDescription(getUserName());
        }

        @Override
        public void print(TaskListener listener) {
            listener.getLogger().println(Messages.Cause_UserIdCause_ShortDescription(
                    ModelHyperlinkNote.encodeTo("/user/"+getUserId(), getUserName())));
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof UserIdCause && Arrays.equals(new Object[]{userId},
                    new Object[]{((UserIdCause) o).userId});
        }

        @Override
        public int hashCode() {
            return 295 + (this.userId != null ? this.userId.hashCode() : 0);
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
                return Messages.Cause_RemoteCause_ShortDescriptionWithNote(addr, note);
            } else {
                return Messages.Cause_RemoteCause_ShortDescription(addr);
            }
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof RemoteCause && Arrays.equals(new Object[] {addr, note},
                    new Object[] {((RemoteCause)o).addr, ((RemoteCause)o).note});
        }

        @Override
        public int hashCode() {
            int hash = 5;
            hash = 83 * hash + (this.addr != null ? this.addr.hashCode() : 0);
            hash = 83 * hash + (this.note != null ? this.note.hashCode() : 0);
            return hash;
        }
    }
}
