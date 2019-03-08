/*
 * The MIT License
 *
 * Copyright (c) 2019, CloudBees, Inc.
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
package jenkins.security;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.RunListener;
import hudson.model.queue.QueueListener;
import hudson.security.ACL;
import hudson.security.AuthorizationStrategy;
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import hudson.security.LegacyAuthorizationStrategy;
import hudson.util.HttpResponses;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Display an administrative monitor if we expect {@link QueueItemAuthenticator} to be a useful security measure,
 * but either it's not present, or potentially badly configured.
 */
@Extension
@Restricted(NoExternalUse.class)
public class QueueItemAuthenticatorMonitor extends AdministrativeMonitor {
    @Override
    public boolean isActivated() {
        AuthorizationStrategy authorizationStrategy = Jenkins.get().getAuthorizationStrategy();
        if (authorizationStrategy instanceof AuthorizationStrategy.Unsecured) {
            return false;
        }
        if (authorizationStrategy instanceof LegacyAuthorizationStrategy) {
            return false;
        }
        if (authorizationStrategy instanceof FullControlOnceLoggedInAuthorizationStrategy) {
            return false;
        }
        return !isQueueItemAuthenticatorPresent() || !isQueueItemAuthenticatorConfigured() || isAnyBuildLaunchedAsSystemWithAuthenticatorPresent();
    }

    @RequirePOST
    public HttpResponse doAct(@QueryParameter String redirect, @QueryParameter String dismiss, @QueryParameter String reset) throws IOException {
        if (redirect != null) {
            return HttpResponses.redirectTo("https://jenkins.io/redirect/queue-item-security");
        }
        if (dismiss != null) {
            this.disable(true);
        }
        if (reset != null) {
            synchronized (buildsLaunchedAsSystemWithAuthenticatorPresentByJob) {
                buildsLaunchedAsSystemWithAuthenticatorPresentByJob.clear();
            }
        }
        return HttpResponses.forwardToPreviousPage();
    }

    public static boolean isQueueItemAuthenticatorPresent() {
        // there is no QueueItemAuthenticatorDescriptor; perhaps you need to install authorize-project?
        return !QueueItemAuthenticatorDescriptor.all().isEmpty();
    }

    public static boolean isQueueItemAuthenticatorConfigured() {
        // there is no configured QueueItemAuthenticator; perhaps you need to configure it?
        return !QueueItemAuthenticatorConfiguration.get().getAuthenticators().isEmpty();
    }

    public boolean isAnyBuildLaunchedAsSystemWithAuthenticatorPresent() {
        // you configured a QueueItemAuthenticator, but builds are still running as SYSTEM
        return !buildsLaunchedAsSystemWithAuthenticatorPresentByJob.isEmpty();
    }

    @Override
    public String getDisplayName() {
        return Messages.QueueItemAuthenticatorMonitor_DisplayName();
    }

    @Restricted(NoExternalUse.class)
    @Extension
    public static class QueueListenerImpl extends QueueListener {
        @Override
        public void onLeft(Queue.LeftItem li) {
            if (li.isCancelled()) {
                return;
            }

            if (!isQueueItemAuthenticatorPresent() || !isQueueItemAuthenticatorConfigured()) {
                // ignore while this is not configured
                return;
            }

            // XXX This can probably be done better, unsure what the expected priority order should be
            String displayName = li.getDisplayName();
            if (displayName == null && li.task != null) {
                displayName = li.task.getFullDisplayName();
            }
            // Do not need to check Executable as alternative while Queue#onStartExecuting(Executor) is called before Task#createExecutable() from Executable.

            if (!(li.task instanceof Job<?, ?>)) {
                // Only care about jobs for now -- Do not react to folder scans and similar
                LOGGER.log(Level.FINE, displayName + " is not a job");
                return;
            }

            // TODO this is probably not intended to be used as a getter -- seems potentially unstable
            Authentication buildAuthentication = li.authenticate();
            boolean buildRunsAsSystem = buildAuthentication == ACL.SYSTEM;
            if (!buildRunsAsSystem) {
                LOGGER.log(Level.FINE, displayName + " does not run as SYSTEM");
                return;
            }

            LOGGER.log(Level.FINE, displayName + " is running as SYSTEM");
            if (isQueueItemAuthenticatorConfigured()) {
                QueueIdAndBuildNumber reference = new QueueIdAndBuildNumber(li.getId());
                synchronized (buildsLaunchedAsSystemWithAuthenticatorPresentByJob) {
                    buildsLaunchedAsSystemWithAuthenticatorPresentByJob.putIfAbsent(((Job) li.task).getFullName(), new TreeSet<>());
                    buildsLaunchedAsSystemWithAuthenticatorPresentByJob.get(((Job) li.task).getFullName()).add(reference);
                }
            }
        }
    }

    @Extension
    public static class BuildListenerImpl extends RunListener<Run< ?, ?>> {
        @Override
        public void onStarted(Run<?, ?> run, TaskListener listener) {
            Job<?, ?> job = run.getParent();
            long queueId = run.getQueueId();
            int buildNumber = run.getNumber();
            QueueIdAndBuildNumber wrappedQueueId = new QueueIdAndBuildNumber(queueId, buildNumber);

            synchronized (buildsLaunchedAsSystemWithAuthenticatorPresentByJob) {
                if (buildsLaunchedAsSystemWithAuthenticatorPresentByJob.containsKey(job.getFullName())) {
                    SortedSet<QueueIdAndBuildNumber> recordedQueueItems = buildsLaunchedAsSystemWithAuthenticatorPresentByJob.get(job.getFullName());
                    if (recordedQueueItems.contains(wrappedQueueId)) {
                        // we recorded this queue item leaving the queue, so update the record with the build number
                        recordedQueueItems.remove(wrappedQueueId); // equals/hashCode only look at queueId, so this works
                        recordedQueueItems.add(wrappedQueueId);
                        while (recordedQueueItems.size() > 10) {
                            // limit to 10 items
                            recordedQueueItems.remove(recordedQueueItems.first());
                        }
                    }
                }
            }
        }
    }

    private static final class QueueIdAndBuildNumber implements Comparable<QueueIdAndBuildNumber> {
        long queueId;
        Long buildNumber;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            QueueIdAndBuildNumber that = (QueueIdAndBuildNumber) o;
            return queueId == that.queueId;
        }

        public QueueIdAndBuildNumber(long queueId) {
            this.queueId = queueId;
        }

        public QueueIdAndBuildNumber(long queueId, long buildNumber) {
            this.queueId = queueId;
            this.buildNumber = buildNumber;
        }

        @Override
        public int hashCode() {
            return Objects.hash(queueId);
        }

        @Override
        public int compareTo(QueueIdAndBuildNumber o) {
            return Long.compare(queueId, o.queueId);
        }
    }

    @Extension
    public static class ItemListenerImpl extends ItemListener {
        @Override
        public void onDeleted(Item item) {
            synchronized (buildsLaunchedAsSystemWithAuthenticatorPresentByJob) {
                buildsLaunchedAsSystemWithAuthenticatorPresentByJob.remove(item.getFullName());
            }
        }

        @Override
        public void onLocationChanged(Item item, String oldFullName, String newFullName) {
            synchronized (buildsLaunchedAsSystemWithAuthenticatorPresentByJob) {
                if (buildsLaunchedAsSystemWithAuthenticatorPresentByJob.containsKey(oldFullName)) {
                    SortedSet<QueueIdAndBuildNumber> references = buildsLaunchedAsSystemWithAuthenticatorPresentByJob.get(oldFullName);
                    buildsLaunchedAsSystemWithAuthenticatorPresentByJob.remove(oldFullName);
                    buildsLaunchedAsSystemWithAuthenticatorPresentByJob.put(newFullName, references);
                }
            }
        }
    }

    /**
     * Records queue IDs and later build numbers of builds started as SYSTEM if there is an authenticator present.
     * This is a relatively memory efficient way to get this information.
     */
    @Restricted(NoExternalUse.class)
    public static final Map<String, SortedSet<QueueIdAndBuildNumber>> buildsLaunchedAsSystemWithAuthenticatorPresentByJob = new ConcurrentHashMap<>();

    private static final Logger LOGGER = Logger.getLogger(QueueItemAuthenticatorMonitor.class.getName());
}
