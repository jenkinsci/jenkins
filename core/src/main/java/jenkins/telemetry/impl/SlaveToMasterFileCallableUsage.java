/*
 * The MIT License
 *
 * Copyright 2021 CloudBees, Inc.
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
import hudson.Functions;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import jenkins.SlaveToMasterFileCallable;
import jenkins.security.s2m.DefaultFilePathFilter;
import jenkins.telemetry.Telemetry;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Records when {@link DefaultFilePathFilter} found {@link SlaveToMasterFileCallable} or similar being used.
 */
@Extension
@Restricted(NoExternalUse.class)
public class SlaveToMasterFileCallableUsage extends Telemetry {

    private Set<String> traces = new TreeSet<>();

    @Override
    public String getDisplayName() {
        return "Access to files on controllers from code running on an agent";
    }

    @Override
    public LocalDate getStart() {
        return LocalDate.of(2021, 11, 4); // https://www.jenkins.io/security/advisory/2021-11-04/
    }

    @Override
    public LocalDate getEnd() {
        return LocalDate.of(2022, 3, 1);
    }

    @Override
    public synchronized JSONObject createContent() {
        Map<String, Object> info = new TreeMap<>();
        info.put("traces", traces);
        info.put("components", buildComponentInformation());
        JSONObject json = JSONObject.fromObject(info);
        traces.clear();
        return json;
    }

    public synchronized void recordTrace(Throwable trace) {
        traces.add(generalize(Functions.printThrowable(trace)));
    }

    protected static String generalize(String trace) {
        return trace
                .replaceAll("@[a-f0-9]+", "@…")
                .replaceAll("]\\([0-9]+\\) created at", "](…) created at")
                .replaceAll("com[.]sun[.]proxy[.][$]Proxy[0-9]+[.]", Matcher.quoteReplacement("com.sun.proxy.$Proxy…."));
    }
}
