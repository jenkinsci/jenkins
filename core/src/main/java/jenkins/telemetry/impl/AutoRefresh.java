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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

@Extension
@Restricted(NoExternalUse.class)
public class AutoRefresh extends Telemetry {

    private static final Map<Boolean, Set<String>> sessionsBySetting = new ConcurrentSkipListMap<>();

    @Nonnull
    @Override
    public String getId() {
        return AutoRefresh.class.getName();
    }

    @Nonnull
    @Override
    public String getDisplayName() {
        return "Use of auto refresh feature";
    }

    @Nonnull
    @Override
    public LocalDate getStart() {
        return LocalDate.of(2019, 2, 10);
    }

    @Nonnull
    @Override
    public LocalDate getEnd() {
        return LocalDate.of(2019, 4, 10);
    }

    @Override
    public JSONObject createContent() {
        if (sessionsBySetting.size() == 0) {
            return null;
        }
        Map<Boolean, Set<String>> currentSessions = new TreeMap<>(sessionsBySetting);
        sessionsBySetting.clear();

        JSONObject payload = new JSONObject();
        for (Map.Entry<Boolean, Set<String>> entry : currentSessions.entrySet()) {
            payload.put(entry.getKey().toString(), entry.getValue().size());
        }
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
            sessionsBySetting.putIfAbsent(enabled, new HashSet<>());
            sessionsBySetting.get(enabled).add(sessionId);
        }
    }
}
