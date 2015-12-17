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

import jenkins.model.Jenkins;
import hudson.Functions;
import hudson.model.Computer;
import hudson.model.User;

import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Exported;

import javax.annotation.Nonnull;
import java.util.Date;

/**
 * Represents a cause that puts a {@linkplain Computer#isOffline() computer offline}.
 *
 * <h2>Views</h2>
 * <p>
 * {@link OfflineCause} must have <tt>cause.jelly</tt> that renders a cause
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
    public final @Nonnull Date getTime() {
        return new Date(timestamp);
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

        @Exported(name="description") @Override
        public String toString() {
            return description.toString();
        }
    }

    public static OfflineCause create(Localizable d) {
        if (d==null)    return null;
        return new SimpleOfflineCause(d);
    }

    /**
     * Caused by unexpected channel termination.
     */
    public static class ChannelTermination extends OfflineCause {
        @Exported
        public final Exception cause;

        public ChannelTermination(Exception cause) {
            this.cause = cause;
        }

        public String getShortDescription() {
            return cause.toString();
        }

        @Override public String toString() {
            return Messages.OfflineCause_connection_was_broken_(Functions.printThrowable(cause));
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
     * @since 1.551
     */
    public static class UserCause extends SimpleOfflineCause {
        private final User user;

        public UserCause(User user, String message) {
            super(hudson.slaves.Messages._SlaveComputer_DisconnectedBy(
                    user!=null ? user.getId() : Jenkins.ANONYMOUS.getName(),
                    message != null ? " : " + message : ""
            ));
            this.user = user;
        }

        public User getUser() {
            return user;
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
     * @since TODO
     */
    public static class IdleOfflineCause extends SimpleOfflineCause {
        public IdleOfflineCause () {
            super(hudson.slaves.Messages._RetentionStrategy_Demand_OfflineIdle());
        }
    }
}
