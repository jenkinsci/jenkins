/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package jenkins.triggers;

import hudson.model.Action;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.TaskListener;
import hudson.model.queue.QueueTaskFuture;
import hudson.scm.PollingResult;
import hudson.triggers.SCMTrigger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.ParameterizedJobMixIn;

/**
 * The item type accepted by {@link SCMTrigger}.
 * @since TODO
 */
public interface SCMTriggerItem {

    /** Should be {@code this}. */
    Item asItem();

    /** @see Job#getNextBuildNumber */
    int getNextBuildNumber();

    /** @see jenkins.model.ParameterizedJobMixIn.ParameterizedJob#getQuietPeriod */
    int getQuietPeriod();

    /** @see ParameterizedJobMixIn#scheduleBuild2 */
    @CheckForNull QueueTaskFuture<?> scheduleBuild2(int quietPeriod, Action... actions);

    /**
     * Checks if there is any update in SCM.
     *
     * <p>
     * The implementation is responsible for ensuring mutual exclusion between polling and builds
     * if necessary.
     */
    @Nonnull PollingResult poll(@Nonnull TaskListener listener);

    /**
     * Provides a display name that indicates what SCM is being used here.
     */
    String getSCMDisplayName();

}
