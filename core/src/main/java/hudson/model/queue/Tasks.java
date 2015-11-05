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

import hudson.model.Queue.Item;
import hudson.model.Queue.Task;
import hudson.security.ACL;
import org.acegisecurity.Authentication;

import java.util.Collection;
import java.util.Collections;
import javax.annotation.Nonnull;
import jenkins.security.QueueItemAuthenticator;
import jenkins.security.QueueItemAuthenticatorConfiguration;

/**
 * Convenience methods around {@link Task} and {@link SubTask}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.377
 */
public class Tasks {

    public static Collection<? extends SubTask> getSubTasksOf(Task task) {
        try {
            return task.getSubTasks();
        } catch (AbstractMethodError e) {
            return Collections.singleton(task);
        }
    }

    public static Object getSameNodeConstraintOf(SubTask t) {
        try {
            return t.getSameNodeConstraint();
        } catch (AbstractMethodError e) {
            return null;
        }
    }

    public static @Nonnull Task getOwnerTaskOf(@Nonnull SubTask t) {
        try {
            return t.getOwnerTask();
        } catch (AbstractMethodError e) {
            return (Task)t;
        }
    }

    /**
     * Helper method to safely invoke {@link Task#getDefaultAuthentication()} on classes that may come
     * from plugins compiled against an earlier version of Jenkins.
     *
     * @param t the task
     * @return {@link Task#getDefaultAuthentication()}, or {@link ACL#SYSTEM}
     * @since 1.520
     */
    @Nonnull
    public static Authentication getDefaultAuthenticationOf(Task t) {
        try {
            return t.getDefaultAuthentication();
        } catch (AbstractMethodError e) {
            return ACL.SYSTEM;
        }
    }

    /**
     * Helper method to safely invoke {@link Task#getDefaultAuthentication(Item)} on classes that may come
     * from plugins compiled against an earlier version of Jenkins.
     *
     * @param t    the task
     * @param item the item
     * @return {@link Task#getDefaultAuthentication(hudson.model.Queue.Item)},
     * or {@link Task#getDefaultAuthentication()}, or {@link ACL#SYSTEM}
     * @since 1.592
     */
    @Nonnull
    public static Authentication getDefaultAuthenticationOf(Task t, Item item) {
        try {
            return t.getDefaultAuthentication(item);
        } catch (AbstractMethodError e) {
            return getDefaultAuthenticationOf(t);
        }
    }

    /**
     * Finds what authentication a task is likely to be run under when scheduled.
     * The actual authentication after scheduling ({@link hudson.model.Queue.Item#authenticate}) might differ,
     * in case some {@link QueueItemAuthenticator#authenticate(hudson.model.Queue.Item)} takes (for example) actions into consideration.
     * @param t a task
     * @return an authentication as specified by some {@link QueueItemAuthenticator#authenticate(hudson.model.Queue.Task)}; else {@link #getDefaultAuthenticationOf}
     * @since 1.560
     */
    public static @Nonnull Authentication getAuthenticationOf(@Nonnull Task t) {
        for (QueueItemAuthenticator qia : QueueItemAuthenticatorConfiguration.get().getAuthenticators()) {
            Authentication a = qia.authenticate(t);
            if (a != null) {
                return a;
            }
        }
        return getDefaultAuthenticationOf(t);
    }

}
