/*
 * The MIT License
 *
 * Copyright (c) 2011, CloudBees, Inc.
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
package jenkins.model;

import hudson.console.ModelHyperlinkNote;
import hudson.model.Executor;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.User;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.Serializable;

/**
 * Records why an {@linkplain Executor#interrupt() executor is interrupted}.
 *
 * <h2>View</h2>
 * <tt>summary.groovy/.jelly</tt> should do one-line HTML rendering to be used while rendering
 * "build history" widget, next to the blocking build. By default it simply renders
 * {@link #getShortDescription()} text.
 *
 * <h2>Value equality semantics</h2>
 * <p>
 * Two {@link CauseOfInterruption}s that are {@linkplain Object#equals(Object) equal} will get
 * merged together.
 *
 * <h2>Persistence</h2>
 * The implementation should be serializable both in Java serialization and XStream.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.425
 * @see Executor#interrupt(Result, CauseOfInterruption...)
 * @see InterruptedBuildAction
 */
@ExportedBean
public abstract class CauseOfInterruption implements Serializable {
    /**
     * Human readable description of why the build is cancelled.
     */
    @Exported
    public abstract String getShortDescription();

    /**
     * Report a line to the listener about this cause.
     */
    public void print(TaskListener listener) {
        listener.getLogger().println(getShortDescription());
    }

    /**
     * Indicates that the build was interrupted from UI.
     */
    public static final class UserInterruption extends CauseOfInterruption {
        private final String user;

        public UserInterruption(User user) {
            this.user = user.getId();
        }

        public UserInterruption(String userId) {
            this.user = userId;
        }

        public User getUser() {
            return User.get(user);
        }

        public String getShortDescription() {
            return Messages.CauseOfInterruption_ShortDescription(user);
        }

        @Override
        public void print(TaskListener listener) {
            listener.getLogger().println(
                Messages.CauseOfInterruption_ShortDescription(ModelHyperlinkNote.encodeTo(getUser())));
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            UserInterruption that = (UserInterruption) o;
            return user.equals(that.user);
        }

        @Override
        public int hashCode() {
            return user.hashCode();
        }

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
