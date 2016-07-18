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
import hudson.scm.NullSCM;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.triggers.SCMTrigger;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.scm.SCMDecisionHandler;

/**
 * The item type accepted by {@link SCMTrigger}.
 * @since 1.568
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
     * <p>
     * The implementation is responsible for checking the {@link SCMDecisionHandler} before proceeding
     * with the actual polling.
     */
    @Nonnull PollingResult poll(@Nonnull TaskListener listener);

    @CheckForNull SCMTrigger getSCMTrigger();

    /**
     * Obtains all active SCMs.
     * May be used for informational purposes, or to determine whether to initiate polling.
     * @return a possibly empty collection
     */
    @Nonnull Collection<? extends SCM> getSCMs();

    /**
     * Utilities.
     */
    class SCMTriggerItems {

        /**
         * See whether an item can be coerced to {@link SCMTriggerItem}.
         * @param item any item
         * @return itself, if a {@link SCMTriggerItem}, or an adapter, if an {@link hudson.model.SCMedItem}, else null
         */
        @SuppressWarnings("deprecation")
        public static @CheckForNull SCMTriggerItem asSCMTriggerItem(Item item) {
            if (item instanceof SCMTriggerItem) {
                return (SCMTriggerItem) item;
            } else if (item instanceof hudson.model.SCMedItem) {
                return new Bridge((hudson.model.SCMedItem) item);
            } else {
                return null;
            }
        }

        private static final class Bridge implements SCMTriggerItem {
            private final hudson.model.SCMedItem delegate;
            Bridge(hudson.model.SCMedItem delegate) {
                this.delegate = delegate;
            }
            @Override public Item asItem() {
                return delegate.asProject();
            }
            @Override public int getNextBuildNumber() {
                return delegate.asProject().getNextBuildNumber();
            }
            @Override public int getQuietPeriod() {
                return delegate.asProject().getQuietPeriod();
            }
            @Override public QueueTaskFuture<?> scheduleBuild2(int quietPeriod, Action... actions) {
                return delegate.asProject().scheduleBuild2(quietPeriod, null, actions);
            }
            @Override public PollingResult poll(TaskListener listener) {
                SCMDecisionHandler veto = SCMDecisionHandler.firstShouldPollVeto(asItem());
                if (veto != null && !veto.shouldPoll(asItem())) {
                    listener.getLogger().println(Messages.SCMTriggerItem_PollingVetoed(veto));
                    return PollingResult.NO_CHANGES;
                }
                return delegate.poll(listener);
            }
            @Override public SCMTrigger getSCMTrigger() {
                return delegate.asProject().getTrigger(SCMTrigger.class);
            }
            @Override public Collection<? extends SCM> getSCMs() {
                return resolveMultiScmIfConfigured(delegate.asProject().getScm());
            }
        }

        public static @Nonnull Collection<? extends SCM> resolveMultiScmIfConfigured(@CheckForNull SCM scm) {
            if (scm == null || scm instanceof NullSCM) {
                return Collections.emptySet();
            } else if (scm.getClass().getName().equals("org.jenkinsci.plugins.multiplescms.MultiSCM")) {
                try {
                    return (Collection<? extends SCM>) scm.getClass().getMethod("getConfiguredSCMs").invoke(scm);
                } catch (Exception x) {
                    Logger.getLogger(SCMTriggerItem.class.getName()).log(Level.WARNING, null, x);
                    return Collections.singleton(scm);
                }
            } else {
                return Collections.singleton(scm);
            }
        }

        private SCMTriggerItems() {}

    }

}
