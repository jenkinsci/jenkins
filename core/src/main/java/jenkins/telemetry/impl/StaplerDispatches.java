/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;
import jenkins.telemetry.Telemetry;
import net.sf.json.JSONObject;
import org.kohsuke.MetaInfServices;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.EvaluationTrace;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Telemetry implementation gathering information about Stapler dispatch routes.
 */
@Extension
@Restricted(NoExternalUse.class)
public class StaplerDispatches extends Telemetry {
    @NonNull
    @Override
    public LocalDate getStart() {
        return LocalDate.of(2018, 10, 10);
    }

    @NonNull
    @Override
    public LocalDate getEnd() {
        return LocalDate.of(2019, 8, 1);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Stapler request handling";
    }

    @Override
    public JSONObject createContent() {
        if (traces.isEmpty()) {
            return null;
        }
        Map<String, Object> info = new TreeMap<>();
        info.put("components", buildComponentInformation());
        info.put("dispatches", buildDispatches());

        return JSONObject.fromObject(info);
    }

    private Object buildDispatches() {
        Set<String> currentTraces = new TreeSet<>(traces);
        traces.clear();
        return currentTraces;
    }

    @MetaInfServices
    public static class StaplerTrace extends EvaluationTrace.ApplicationTracer {

        @Override
        protected void record(StaplerRequest2 staplerRequest, String s) {
            if (Telemetry.isDisabled()) {
                // do not collect traces while usage statistics are disabled
                return;
            }
            traces.add(s);
        }
    }

    private static final Set<String> traces = new ConcurrentSkipListSet<>();
}
