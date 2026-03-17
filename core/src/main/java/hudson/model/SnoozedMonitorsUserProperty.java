/*
 * The MIT License
 *
 * Copyright (c) 2026 Jenkins Contributors
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

package hudson.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.userproperty.UserPropertyCategory;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Per-user storage for snoozed administrative monitors.
 *
 * <p>Each entry maps a monitor ID to an expiry timestamp (milliseconds since epoch).
 * Expired entries are lazily cleaned up when queried.</p>
 *
 * @since TODO
 */
@Restricted(NoExternalUse.class)
public class SnoozedMonitorsUserProperty extends UserProperty {

    private volatile Map<String, Long> snoozedMonitors;

    public SnoozedMonitorsUserProperty() {
        this.snoozedMonitors = new ConcurrentHashMap<>();
    }

    /**
     * Check whether the given monitor is snoozed for this user.
     *
     * @param monitorId the administrative monitor ID
     * @return {@code true} if the monitor is snoozed and the snooze has not expired
     */
    public boolean isSnoozed(String monitorId) {
        if (snoozedMonitors == null) {
            return false;
        }
        Long expiry = snoozedMonitors.get(monitorId);
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() >= expiry) {
            snoozedMonitors.remove(monitorId);
            return false;
        }
        return true;
    }

    /**
     * Snooze the given monitor for this user.
     *
     * @param monitorId the administrative monitor ID
     * @param durationMs the snooze duration in milliseconds, must be positive
     * @throws IOException if persisting the user configuration fails
     */
    public void snooze(String monitorId, long durationMs) throws IOException {
        if (snoozedMonitors == null) {
            snoozedMonitors = new ConcurrentHashMap<>();
        }
        snoozedMonitors.put(monitorId, System.currentTimeMillis() + durationMs);
        user.save();
    }

    /**
     * Remove the snooze for the given monitor for this user.
     *
     * @param monitorId the administrative monitor ID
     * @throws IOException if persisting the user configuration fails
     */
    public void unsnooze(String monitorId) throws IOException {
        if (snoozedMonitors != null && snoozedMonitors.remove(monitorId) != null) {
            user.save();
        }
    }

    /**
     * Remove all expired snooze entries.
     */
    void cleanupExpired() {
        if (snoozedMonitors == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> it = snoozedMonitors.entrySet().iterator();
        while (it.hasNext()) {
            if (now >= it.next().getValue()) {
                it.remove();
            }
        }
    }

    private Object readResolve() {
        if (snoozedMonitors == null) {
            snoozedMonitors = new ConcurrentHashMap<>();
        } else if (!(snoozedMonitors instanceof ConcurrentHashMap)) {
            snoozedMonitors = new ConcurrentHashMap<>(snoozedMonitors);
        }
        return this;
    }

    /**
     * Get the property for the current user, or {@code null} if anonymous.
     *
     * @return the property instance, or {@code null}
     */
    public static SnoozedMonitorsUserProperty forCurrentUser() {
        User current = User.current();
        if (current == null) {
            return null;
        }
        return current.getProperty(SnoozedMonitorsUserProperty.class);
    }

    @Extension
    @Symbol("snoozedMonitors")
    public static class DescriptorImpl extends UserPropertyDescriptor {

        @Override
        public UserProperty newInstance(User user) {
            return new SnoozedMonitorsUserProperty();
        }

        @Override
        public boolean isEnabled() {
            return false;
        }

        @Override
        public @NonNull UserPropertyCategory getUserPropertyCategory() {
            return UserPropertyCategory.get(UserPropertyCategory.Invisible.class);
        }
    }
}
