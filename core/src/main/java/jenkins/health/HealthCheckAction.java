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

package jenkins.health;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.model.InvisibleAction;
import hudson.model.UnprotectedRootAction;
import hudson.util.RemotingDiagnostics;
import java.io.IOException;
import java.time.Duration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.util.JenkinsJVM;
import jenkins.util.SystemProperties;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.json.JsonHttpResponse;

/**
 * Provides a health check action for Jenkins.
 */
@Extension
@Restricted(NoExternalUse.class)
public final class HealthCheckAction extends InvisibleAction implements UnprotectedRootAction {

    private static final Logger LOGGER = Logger.getLogger(HealthCheckAction.class.getName());
    private static final Duration THRESHOLD_TIMEOUT = SystemProperties.getDuration(
        HealthCheckAction.class.getName() + ".thresholdTimeout", Duration.ofSeconds(10));

    @Override
    public String getUrlName() {
        return "health";
    }

    public HttpResponse doIndex() {
        boolean success = true;
        var failing = new JSONArray();

        var watchdog = new Timer("HealthCheckActionWatchdog", true);
        watchdog.schedule(new TimerTask() {
            @Override
            public void run() {
                if (JenkinsJVM.isJenkinsJVM()) {
                    try {
                        var threadDump = RemotingDiagnostics.getThreadDump(FilePath.localChannel);
                        LOGGER.severe(() -> "health check did not complete in timely fashion:\n\n"
                                            + threadDump.values().stream().collect(Collectors.joining()).trim());
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Failed to get thread dump during slow health check", e);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }, THRESHOLD_TIMEOUT.toMillis());

        try {
            for (var healthCheck : ExtensionList.lookup(HealthCheck.class)) {
                var check = healthCheck.check();
                success &= check;
                if (!check) {
                    failing.add(healthCheck.getName());
                }
            }
            var payload = new JSONObject().element("status", success);
            if (!success) {
                payload = payload.element("failures", failing);
            }
            return new JsonHttpResponse(payload, success ? 200 : 503);
        } finally {
            watchdog.cancel();
        }
    }
}
