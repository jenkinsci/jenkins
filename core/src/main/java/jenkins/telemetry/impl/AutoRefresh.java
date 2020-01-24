/*
 * The MIT License
 *
 * Copyright (c) 2019, Daniel Beck
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
package jenkins.telemetry.impl;

import hudson.Extension;
import jenkins.telemetry.Telemetry;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Extension
@Restricted(NoExternalUse.class)
public class AutoRefresh extends Telemetry {

    private static final Set<String> sessionsWithAutoRefresh = new HashSet<>();
    private static final Set<String> sessionsWithoutAutoRefresh = new HashSet<>();

    @Nonnull
    @Override
    public String getDisplayName() {
        return Messages.AutoRefresh_DisplayName();
    }

    @Nonnull
    @Override
    public LocalDate getStart() {
        return LocalDate.of(2019, 10, 20);
    }

    @Nonnull
    @Override
    public LocalDate getEnd() {
        return LocalDate.of(2019, 12, 31);
    }

    @Override
    public JSONObject createContent() {
        int sessionsWithAutoRefreshCount, sessionsWithoutAutoRefreshCount;
        synchronized (sessionsWithAutoRefresh) {
            sessionsWithAutoRefreshCount = sessionsWithAutoRefresh.size();
            sessionsWithAutoRefresh.clear();
        }
        synchronized (sessionsWithoutAutoRefresh) {
            sessionsWithoutAutoRefreshCount = sessionsWithoutAutoRefresh.size();
            sessionsWithoutAutoRefresh.clear();
        }

        if (sessionsWithAutoRefreshCount == 0 && sessionsWithoutAutoRefreshCount == 0) {
            return null;
        }

        JSONObject payload = new JSONObject();
        payload.put("enabled", sessionsWithAutoRefreshCount);
        payload.put("disabled", sessionsWithoutAutoRefreshCount);
        return payload;
    }

    public void recordRequest(HttpServletRequest request, boolean enabled) {
        if (Telemetry.isDisabled()) {
            return;
        }
        if (!isActivePeriod()) {
            return;
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            String sessionId = session.getId();
            if (enabled) {
                synchronized (sessionsWithAutoRefresh) {
                    sessionsWithAutoRefresh.add(sessionId);
                }
            } else {
                synchronized (sessionsWithoutAutoRefresh) {
                    sessionsWithoutAutoRefresh.add(sessionId);
                }
            }
        }
    }
}
