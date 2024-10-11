/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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

package hudson.slaves;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Computer;
import hudson.model.User;
import java.io.ObjectStreamException;
import java.util.Collections;
import java.util.Date;
import jenkins.model.Jenkins;
import org.jvnet.localizer.Localizable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Represents a cause that puts a {@linkplain Computer#isOffline() computer offline}.
 *
 * <h2>Views</h2>
 * <p>
 * {@link OfflineCause} must have {@code cause.jelly} that renders a cause
 * into HTML. This is used to tell users why the node is put offline.
 * This view should render a block element like DIV.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.320
 */
@ExportedBean
public abstract class OfflineCause {
    protected final long timestamp = System.currentTimeMillis();

    /**
     * Timestamp in which the event happened.
     *
     * @since 1.612
     */
    @Exported
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Same as {@link #getTimestamp()} but in a different type.
     *
     * @since 1.612
     */
    public final @NonNull Date getTime() {
        return new Date(timestamp);
    }

    /**
     * @deprecated Only exists for backward compatibility.
     * @see Computer#setTemporarilyOffline(boolean).
     */
    @Deprecated
    @Restricted(NoExternalUse.class)
    public static class LegacyOfflineCause extends OfflineCause {
        @Exported(name = "description") @Override
        public String toString() {
            return "";
        }
    }

    /**
     * {@link OfflineCause} that renders a static text,
     * but without any further UI.
     */
    public static class SimpleOfflineCause extends OfflineCause {
        public final Localizable description;

        /**
         * @since 1.571
         */
        protected SimpleOfflineCause(Localizable description) {
            this.description = description;
        }

        @Exported(name = "description") @Override
        public String toString() {
            return description.toString();
        }
    }

    public static OfflineCause create(Localizable d) {
        if (d == null)    return null;
        return new SimpleOfflineCause(d);
    }

    /**
     * Caused by unexpected channel termination.
     */
    public static class ChannelTermination extends OfflineCause {
        public final Exception cause;

        public ChannelTermination(Exception cause) {
            this.cause = cause;
        }

        public String getShortDescription() {
            return cause.toString();
        }

        @Override public String toString() {
            return Messages.OfflineCause_connection_was_broken_simple();
        }
    }

    /**
     * Caused by failure to launch.
     */
    public static class LaunchFailed extends OfflineCause {
        @Override
        public String toString() {
            return Messages.OfflineCause_LaunchFailed();
        }
    }

    /**
     * Taken offline by user.
     *
     * @since 1.551
     */
    public static class UserCause extends SimpleOfflineCause {
        @Deprecated
        private transient User user;
        // null when unknown
        private /*final*/ @CheckForNull String userId;

        public UserCause(@CheckForNull User user, @CheckForNull String message) {
            this(
                    user != null ? user.getId() : null,
                    message != null ? " : " + message : ""
            );
        }

        private UserCause(String userId, String message) {
            super(hudson.slaves.Messages._SlaveComputer_DisconnectedBy(userId != null ? userId : Jenkins.ANONYMOUS2.getName(), message));
            this.userId = userId;
        }

        public User getUser() {
            return userId == null
                    ? User.getUnknown()
                    : User.getById(userId, true)
            ;
        }

        // Storing the User in a filed was a mistake, switch to userId
        private Object readResolve() throws ObjectStreamException {
            if (user != null) {
                String id = user.getId();
                if (id != null) {
                    userId = id;
                } else {
                    // The user field is not properly deserialized so id may be missing. Look the user up by fullname
                    User user = User.get(this.user.getFullName(), true, Collections.emptyMap());
                    userId = user.getId();
                }
                this.user = null;
            }
            return this;
        }
    }

    public static class ByCLI extends UserCause {
        @Exported
        public final String message;

        public ByCLI(String message) {
            super(User.current(), message);
            this.message = message;
        }
    }

    /**
     * Caused by idle period.
     * @since 1.644
     */
    public static class IdleOfflineCause extends SimpleOfflineCause {
        public IdleOfflineCause() {
            super(hudson.slaves.Messages._RetentionStrategy_Demand_OfflineIdle());
        }
    }
}
