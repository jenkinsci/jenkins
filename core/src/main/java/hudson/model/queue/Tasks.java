/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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
package hudson.model.queue;

import hudson.model.Queue;
import hudson.model.Queue.Item;
import hudson.model.Queue.Task;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.util.Collection;
import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.security.QueueItemAuthenticator;
import jenkins.security.QueueItemAuthenticatorProvider;
import org.springframework.security.core.Authentication;

/**
 * Convenience methods around {@link Task} and {@link SubTask}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.377
 */
public class Tasks {

    /** @deprecated call {@link Task#getSubTasks} directly */
    @Deprecated
    public static Collection<? extends SubTask> getSubTasksOf(Task task) {
        return task.getSubTasks();
    }

    /** @deprecated call {@link SubTask#getSameNodeConstraint} directly */
    @Deprecated
    public static Object getSameNodeConstraintOf(SubTask t) {
        return t.getSameNodeConstraint();
    }

    /** deprecated call {@link SubTask#getOwnerTask} directly */
    @Deprecated
    public static @NonNull Task getOwnerTaskOf(@NonNull SubTask t) {
        return t.getOwnerTask();
    }

    /**
     * Gets the {@link hudson.model.Item} most closely associated with the supplied {@link SubTask}.
     * @param t the {@link SubTask}.
     * @return the {@link hudson.model.Item} associated with the {@link SubTask} or {@code null} if this
     * {@link SubTask} is not associated with an {@link hudson.model.Item}
     * @since 2.55
     */
    @CheckForNull
    public static hudson.model.Item getItemOf(@NonNull SubTask t) {
        Queue.Task p = t.getOwnerTask();
        while (!(p instanceof hudson.model.Item)) {
            Queue.Task o = p.getOwnerTask();
            if (o == p) {
                break;
            }
            p = o;
        }
        return p instanceof hudson.model.Item ? (hudson.model.Item)p : null;
    }

    /** @deprecated call {@link Queue.Task#getDefaultAuthentication()} directly */
    @Deprecated
    @NonNull
    public static org.acegisecurity.Authentication getDefaultAuthenticationOf(Task t) {
        return t.getDefaultAuthentication();
    }

    /** @deprecated call {@link Queue.Task#getDefaultAuthentication(Item)} directly */
    @Deprecated
    @NonNull
    public static org.acegisecurity.Authentication getDefaultAuthenticationOf(Task t, Item item) {
        return t.getDefaultAuthentication(item);
    }

    /**
     * Finds what authentication a task is likely to be run under when scheduled.
     * The actual authentication after scheduling ({@link hudson.model.Queue.Item#authenticate2}) might differ,
     * in case some {@link QueueItemAuthenticator#authenticate2(hudson.model.Queue.Item)} takes (for example) actions into consideration.
     * @param t a task
     * @return an authentication as specified by some {@link QueueItemAuthenticator#authenticate2(hudson.model.Queue.Task)}; else {@link Task#getDefaultAuthentication2()}
     * @since TODO
     */
    public static @NonNull Authentication getAuthenticationOf2(@NonNull Task t) {
        for (QueueItemAuthenticator qia : QueueItemAuthenticatorProvider.authenticators()) {
            Authentication a = qia.authenticate2(t);
            if (a != null) {
                return a;
            }
        }
        return t.getDefaultAuthentication2();
    }

    /**
     * @deprecated use {@link #getAuthenticationOf2}
     * @since 1.560
     */
    @Deprecated
    public static @NonNull org.acegisecurity.Authentication getAuthenticationOf(@NonNull Task t) {
        return org.acegisecurity.Authentication.fromSpring(getAuthenticationOf2(t));
    }


}
