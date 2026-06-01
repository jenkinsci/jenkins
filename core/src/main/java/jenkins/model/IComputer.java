/*
 * The MIT License
 *
 * Copyright 2024 CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.ModelObject;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import java.util.List;
import java.util.concurrent.Future;
import jenkins.agents.IOfflineCause;
import org.jenkins.ui.icon.Icon;
import org.jenkins.ui.icon.IconSet;
import org.jenkins.ui.icon.IconSpec;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;

/**
 * Interface for computer-like objects meant to be passed to {@code t:executors} tag.
 *
 * @since 2.480
 */
@Restricted(Beta.class)
public interface IComputer extends AccessControlled, IconSpec, ModelObject, Named {
    /**
     * Used to render the list of executors.
     * @return a snapshot of the executor display information
     */
    @NonNull
    List<? extends IDisplayExecutor> getDisplayExecutors();

    /**
     * Returns whether the agent is offline for scheduling new tasks.
     * Even if {@code true}, the agent may still be connected to the controller and executing a task,
     * but is considered offline for scheduling.
     * @return {@code true} if the agent is offline; {@code false} if online.
     * @see #isConnected()
     */
    boolean isOffline();

    /**
     * Returns {@code true} if the computer is accepting tasks. Needed to allow agents programmatic suspension of task
     * scheduling that does not overlap with being offline.
     *
     * @return {@code true} if the computer is accepting tasks
     * @see hudson.slaves.RetentionStrategy#isAcceptingTasks(Computer)
     * @see hudson.model.Node#isAcceptingTasks()
     */
    boolean isAcceptingTasks();

    /**
     * @return the URL where to reach specifically this computer, relative to Jenkins URL.
     */
    @NonNull
    String getUrl();

    /**
     * @return {@code true} if this computer has a defined offline cause, @{code false} otherwise.
     */
    default boolean hasOfflineCause() {
        return Util.fixEmpty(getOfflineCauseReason()) != null;
    }

    /**
     * @return the offline cause if the computer is offline.
     * @since 2.483
     */
    IOfflineCause getOfflineCause();

    /**
     * If the computer was offline (either temporarily or not),
     * this method will return the cause as a string (without user info).
     * <p>
     * {@code hasOfflineCause() == true} implies this must be nonempty.
     *
     * @return
     *      empty string if the system was put offline without given a cause.
     */
    @NonNull
    default String getOfflineCauseReason() {
        if (getOfflineCause() == null) {
            return "";
        }
        return getOfflineCause().getReason();
    }

    /**
     * @return true if the node is currently connecting to the Jenkins controller.
     */
    boolean isConnecting();

    /**
     * Returns the icon for this computer.
     * <p>
     * It is both the recommended and default implementation to serve different icons based on {@link #isOffline}
     *
     * @see #getIconClassName()
     */
    default String getIcon() {
        // The computer is not accepting tasks, e.g. because the availability demands it being offline.
        if (!isAcceptingTasks()) {
            return "symbol-computer-not-accepting";
        }
        var offlineCause = getOfflineCause();
        if (offlineCause != null) {
            return offlineCause.getComputerIcon();
        }
        // The computer is not connected or it is temporarily offline due to a node monitor
        if (isOffline()) return "symbol-computer-offline";
        return "symbol-computer";
    }

    /**
     * Returns the alternative text for the computer icon.
     */
    @SuppressWarnings("unused") // jelly
    default String getIconAltText() {
        if (!isAcceptingTasks()) {
            return "[suspended]";
        }
        var offlineCause = getOfflineCause();
        if (offlineCause != null) {
            return offlineCause.getComputerIconAltText();
        }
        // There is a "technical" reason the computer will not accept new builds
        if (isOffline()) return "[offline]";
        return "[online]";
    }

    default String getTooltip() {
        var offlineCause = getOfflineCause();
        if (offlineCause != null) {
            return offlineCause.toString();
        } else {
            return "";
        }
    }

    /**
     * Returns the class name that will be used to look up the icon.
     * <p>
     * This class name will be added as a class tag to the html img tags where the icon should
     * show up followed by a size specifier given by {@link Icon#toNormalizedIconSizeClass(String)}
     * The conversion of class tag to src tag is registered through {@link IconSet#addIcon(Icon)}
     *
     * @see #getIcon()
     */
    default String getIconClassName() {
        return getIcon();
    }

    /**
     * Returns the number of {@link IExecutor}s that are doing some work right now.
     */
    int countBusy();

    /**
     * Returns the current size of the executor pool for this computer.
     */
    int countExecutors();

    /**
     * Indicates whether the agent can accept a new task when it becomes idle.
     * {@code false} does not necessarily mean the agent is disconnected.
     * @return {@code true} if the agent is online.
     * @see #isConnected()
     */
    boolean isOnline();

    /**
     * Indicates whether the agent is actually connected to the controller.
     * @return {@code true} if the agent is connected to the controller.
     */
    default boolean isConnected() {
        return isOnline();
    }

    /**
     * @return the number of {@link IExecutor}s that are idle right now.
     */
    int countIdle();

    /**
     * @return true if this computer can be launched by Jenkins proactively and automatically.
     *
     * <p>
     * For example, inbound agents return {@code false} from this, because the launch process
     * needs to be initiated from the agent side.
     */
    boolean isLaunchSupported();

    /**
     * Attempts to connect this computer.
     *
     * @param forceReconnect If true and a connect activity is already in progress, it will be cancelled and
     *                       the new one will be started. If false, and a connect activity is already in progress, this method
     *                       will do nothing and just return the pending connection operation.
     * @return A {@link Future} representing pending completion of the task. The 'completion' includes
     * both a successful completion and a non-successful completion (such distinction typically doesn't
     * make much sense because as soon as {@link IComputer} is connected it can be disconnected by some other threads.)
     */
    Future<?> connect(boolean forceReconnect);

    @NonNull
    @Override
    default ACL getACL() {
        return Jenkins.get().getAuthorizationStrategy().getACL(this);
    }
}
