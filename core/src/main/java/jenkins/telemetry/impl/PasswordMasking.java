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
     * @param parentContext the parent context type (Item, Computer, View)
     */
    public static void recordMasking(String parentContext) {
        if (Telemetry.isDisabled()) {
            return;
        }

        maskingCounts.computeIfAbsent(parentContext, k -> new AtomicLong(0)).incrementAndGet();
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
        if (maskingCounts.isEmpty()) {
            return null;
        }

        JSONObject payload = new JSONObject();

        payload.put("components", buildComponentInformation());
        payload.put("maskingCounts", maskingCounts);

        return payload;
    }
}
