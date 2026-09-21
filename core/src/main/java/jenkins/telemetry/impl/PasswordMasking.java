/*
 * The MIT License
 *
 * Copyright (c) 2026, CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import jenkins.telemetry.Telemetry;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Telemetry implementation gathering information about password field masking.
 */
@Extension
@Restricted(NoExternalUse.class)
public class PasswordMasking extends Telemetry {

    private static final Map<String, AtomicLong> maskingCounts = new ConcurrentHashMap<>();

    /**
     * Records when password masking occurs.
     *
     * @param className the class name of the object
     * @param closestAncestor the closest ancestor class name
     * @param jellyView the Jelly view where masking occurred
     */
    public static void recordMasking(String className, String closestAncestor, String jellyView) {
        if (Telemetry.isDisabled()) {
            return;
        }

        String key = className + "|" + closestAncestor + "|" + jellyView;
        maskingCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Password field masking";
    }

    @NonNull
    @Override
    public LocalDate getStart() {
        return LocalDate.of(2026, 1, 26);
    }

    @NonNull
    @Override
    public LocalDate getEnd() {
        return LocalDate.of(2026, 7, 26);
    }

    @Override
    public JSONObject createContent() {
        JSONArray events = new JSONArray();
        for (Map.Entry<String, AtomicLong> entry : maskingCounts.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 3);
            if (parts.length == 3) {
                String className = parts[0];
                String closestAncestor = parts[1];
                String jellyView = parts[2];
                long count = entry.getValue().longValue();

                JSONObject event = new JSONObject();
                event.put("className", className);
                event.put("closestAncestor", closestAncestor);
                event.put("jellyView", jellyView);
                event.put("count", count);
                events.add(event);
            }
        }

        maskingCounts.clear();

        JSONObject payload = new JSONObject();
        payload.put("components", buildComponentInformation());
        payload.put("masking", events);

        return payload;
    }
}
