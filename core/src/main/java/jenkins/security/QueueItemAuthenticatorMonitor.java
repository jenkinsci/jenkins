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
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.queue.QueueListener;
import hudson.security.ACL;
import hudson.security.AuthorizationStrategy;
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import hudson.security.LegacyAuthorizationStrategy;
import hudson.util.HttpResponses;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.springframework.security.core.Authentication;

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
            anyBuildLaunchedAsSystemWithAuthenticatorPresent = false;
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
        // you configured a QueueItemAuthenticator, but builds are still running as SYSTEM2
        return anyBuildLaunchedAsSystemWithAuthenticatorPresent;
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

            // XXX This can probably be done better, unsure what the expected priority order is
            String displayName = li.getDisplayName();
            if (displayName == null && li.task != null) {
                displayName = li.task.getFullDisplayName();
            }
            Queue.Executable executable = li.getExecutable();
            if (displayName == null && executable != null) {
                displayName = executable.toString();
            }

            if (!(li.task instanceof Job<?, ?>)) {
                // Only care about jobs for now -- Do not react to folder scans and similar
                LOGGER.log(Level.FINE, displayName + " is not a job");
                return;
            }

            // TODO this is probably not intended to be used as a getter -- seems potentially unstable
            Authentication buildAuthentication = li.authenticate2();
            boolean buildRunsAsSystem = buildAuthentication.equals(ACL.SYSTEM2);
            if (!buildRunsAsSystem) {
                LOGGER.log(Level.FINE, displayName + " does not run as SYSTEM");
                return;
            }

            LOGGER.log(Level.FINE, displayName + " is running as SYSTEM");
            if (isQueueItemAuthenticatorConfigured()) {
                anyBuildLaunchedAsSystemWithAuthenticatorPresent = true;
            }
        }
    }

    private static boolean anyBuildLaunchedAsSystemWithAuthenticatorPresent = false;

    private static final Logger LOGGER = Logger.getLogger(QueueItemAuthenticatorMonitor.class.getName());
}
