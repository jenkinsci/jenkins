/*
 * The MIT License
 *
 * Copyright (c) 2018 CloudBees, Inc.
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
import hudson.ExtensionList;
import hudson.model.ManagementLink;
import hudson.model.ModelObject;
import hudson.security.Permission;
import hudson.util.FormApply;
import jenkins.model.Jenkins;
import jenkins.util.SessionListener;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.CheckForNull;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Restricted(NoExternalUse.class)
@Extension
public class HttpSessionManager extends ManagementLink implements SessionListener {

    public static HttpSessionManager getInstance() {
        return ExtensionList.lookupSingleton(HttpSessionManager.class);
    }

    private final ConcurrentMap<String, HttpSession> activeSessionsById = new ConcurrentHashMap<>();

    @Override
    public void sessionCreated(HttpSessionEvent event) {
        addSession(event.getSession());
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        removeSession(event.getSession());
    }

    @Override
    public void sessionWillPassivate(HttpSessionEvent event) {
        removeSession(event.getSession());
    }

    @Override
    public void sessionDidActivate(HttpSessionEvent event) {
        addSession(event.getSession());
    }

    @Override
    public void sessionIdChanged(HttpSessionEvent event, String oldSessionId) {
        activeSessionsById.remove(oldSessionId);
        addSession(event.getSession());
    }

    @RequirePOST
    public void doInvalidateAllSessions() {
        checkPermissions();
        activeSessionsById.values().forEach(this::tryInvalidateSession);
    }

    @RequirePOST
    public HttpResponse doInvalidateAllSessionsExcept(@QueryParameter(required = true) String sessionId) {
        checkPermissions();
        activeSessionsById.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(sessionId))
                .map(Map.Entry::getValue)
                .forEach(this::tryInvalidateSession);
        return FormApply.success(".");
    }

    @RequirePOST
    public HttpResponse doInvalidateSession(@QueryParameter(required = true) String sessionId) {
        checkPermissions();
        HttpSession session = activeSessionsById.get(sessionId);
        if (session != null) {
            tryInvalidateSession(session);
        }
        return FormApply.success(".");
    }

    public List<SessionDetails> getActiveSessions() {
        checkPermissions();
        return activeSessionsById.values().stream().map(SessionDetails::new).collect(Collectors.toList());
    }

    private void addSession(HttpSession session) {
        activeSessionsById.put(session.getId(), session);
    }

    private void removeSession(HttpSession session) {
        activeSessionsById.remove(session.getId());
    }

    private void tryInvalidateSession(HttpSession session) {
        removeSession(session);
        try {
            session.invalidate();
        } catch (IllegalStateException ignored) {
        }
    }

    private void checkPermissions() {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
    }

    @Override
    public @CheckForNull String getIconFileName() {
        return "user.png";
    }

    @Override
    public @CheckForNull String getUrlName() {
        return "sessions";
    }

    @Override
    public @CheckForNull String getDisplayName() {
        return "Manage Active HTTP Sessions";
    }

    @Override
    public @CheckForNull Permission getRequiredPermission() {
        return Jenkins.ADMINISTER;
    }

    @Restricted(NoExternalUse.class)
    public static class SessionDetails implements ModelObject {
        private final String sessionId;
        private final Instant creationTime;
        private final Instant lastAccessedTime;

        private SessionDetails(HttpSession session) {
            sessionId = session.getId();
            creationTime = Instant.ofEpochMilli(session.getCreationTime());
            lastAccessedTime = Instant.ofEpochMilli(session.getLastAccessedTime());
        }

        public String getSessionId() {
            return sessionId;
        }

        public Instant getCreationTime() {
            return creationTime;
        }

        public Instant getLastAccessedTime() {
            return lastAccessedTime;
        }

        @Override
        public String getDisplayName() {
            return sessionId;
        }
    }
}
