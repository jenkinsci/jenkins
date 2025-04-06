/*
 * The MIT License
 *
 * Copyright (c) 2025, CloudBees, Inc.
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

import static java.util.logging.Level.FINE;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.telemetry.Telemetry;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Collect information about which URLs in {@code /extensionList/} are being accessed.
 *
 * @since TODO
 */
@Extension
@Restricted(NoExternalUse.class)
public class HttpExtensionList extends Telemetry {
    private final Map<String, Integer> calls = new ConcurrentHashMap<>();

    @NonNull
    @Override
    public String getDisplayName() {
        return "Extension List access via HTTP";
    }

    @NonNull
    @Override
    public LocalDate getStart() {
        return LocalDate.of(2025, 4, 5);
    }

    @NonNull
    @Override
    public LocalDate getEnd() {
        return LocalDate.of(2025, 7, 1);
    }

    @Override
    public synchronized JSONObject createContent() {
        JSONObject info = new JSONObject();
        info.put("components", buildComponentInformation());

        Map<String, Integer> currentRequests = new TreeMap<>(calls);
        calls.clear();

        JSONObject payload = new JSONObject();
        payload.putAll(currentRequests);

        info.put("dispatches", payload);
        return info;
    }

    public synchronized void record(String path) {
        String[] parts = path.split("/");
        if (parts.length > 1) {
            // Record just extension point + implementation class
            path = parts[0] + '/' + parts[1];
        }
        calls.compute(path, (p, v) -> v == null ? 1 : v + 1);
    }

    @Extension
    @Restricted(NoExternalUse.class)
    public static class ExtensionListRootAction extends InvisibleAction implements RootAction {
        private static final Logger LOGGER = Logger.getLogger(ExtensionListRootAction.class.getName());

        @Override
        public String getUrlName() {
            return "extensionList";
        }

        public ExtensionList getDynamic(String extensionType) throws ClassNotFoundException {
            StaplerRequest2 req = Stapler.getCurrentRequest2();
            if (req != null && extensionType != null) {
                try {
                    final HttpExtensionList telemetry = ExtensionList.lookupSingleton(HttpExtensionList.class);
                    if (telemetry.isActivePeriod()) {
                        telemetry.record(extensionType + req.getRestOfPath());
                    }
                } catch (Exception ex) {
                    LOGGER.log(FINE, "Failed to record telemetry for " + HttpExtensionList.class.getName(), ex);
                }
            }
            return Jenkins.get().getExtensionList(extensionType);
        }
    }
}
