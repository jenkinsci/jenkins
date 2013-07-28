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

import hudson.model.Queue.Task;
import hudson.security.ACL;
import org.acegisecurity.Authentication;

import java.util.Collection;
import java.util.Collections;

/**
 * Convenience methods around {@link Task} and {@link SubTask}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.377
 */
public class Tasks {

    /**
     * A pointless function to work around what appears to be a HotSpot problem. See JENKINS-5756 and bug 6933067
     * on BugParade for more details.
     */
    private static Collection<? extends SubTask> _getSubTasksOf(Task task) {
        return task.getSubTasks();
    }

    public static Collection<? extends SubTask> getSubTasksOf(Task task) {
        try {
            return _getSubTasksOf(task);
        } catch (AbstractMethodError e) {
            return Collections.singleton(task);
        }
    }

    /**
     * A pointless function to work around what appears to be a HotSpot problem. See JENKINS-5756 and bug 6933067
     * on BugParade for more details.
     */
    private static Object _getSameNodeConstraintOf(SubTask t) {
        return t.getSameNodeConstraint();
    }

    public static Object getSameNodeConstraintOf(SubTask t) {
        try {
            return _getSameNodeConstraintOf(t);
        } catch (AbstractMethodError e) {
            return null;
        }
    }

    /**
     * A pointless function to work around what appears to be a HotSpot problem. See JENKINS-5756 and bug 6933067
     * on BugParade for more details.
     */
    public static Task _getOwnerTaskOf(SubTask t) {
        return t.getOwnerTask();
    }

    public static Task getOwnerTaskOf(SubTask t) {
        try {
            return _getOwnerTaskOf(t);
        } catch (AbstractMethodError e) {
            return (Task)t;
        }
    }

    /**
     * A pointless function to work around what appears to be a HotSpot problem. See JENKINS-5756 and bug 6933067
     * on BugParade for more details.
     */
    private static Authentication _getDefaultAuthenticationOf(Task t) {
        return t.getDefaultAuthentication();
    }

    /**
     * @param t a task
     * @return {@link Task#getDefaultAuthentication}, or {@link ACL#SYSTEM}
     * @since 1.520
     */
    public static Authentication getDefaultAuthenticationOf(Task t) {
        try {
            return _getDefaultAuthenticationOf(t);
        } catch (AbstractMethodError e) {
            return ACL.SYSTEM;
        }
    }

}
