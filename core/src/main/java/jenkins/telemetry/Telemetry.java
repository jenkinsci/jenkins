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
package jenkins.telemetry;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.ProxyConfiguration;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
import hudson.model.UsageStatistics;
import jenkins.model.Jenkins;

import javax.annotation.Nonnull;
import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extension point for collecting JEP-214 telemetry.
 *
 * @link https://github.com/jenkinsci/jep/tree/master/jep/214
 */
public abstract class Telemetry implements ExtensionPoint {
    /**
     * ID of this collector, typically a basic alphanumeric string (and _- characters)
     * 
     * @return ID of the collector, never null or empty
     */
    @Nonnull
    public abstract String getId();

    /**
     * User friendly display name for this telemetry collector, ideally localized.
     *
     * @return display name, never null or empty
     */
    @Nonnull
    public abstract String getDisplayName();

    /**
     * Start date for the collection.
     * Will be checked in Jenkins to not collect outside the defined time span.
     * This does not have to be precise enough for time zones to be a consideration.
     *
     * @return collection start date
     */
    @Nonnull
    public abstract LocalDate getStart();

    /**
     * End date for the collection.
     * Will be checked in Jenkins to not collect outside the defined time span.
     * This does not have to be precise enough for time zones to be a consideration.
     *
     * @return collection end date
     */
    @Nonnull
    public abstract LocalDate getEnd();

    /**
     * Returns the content to be sent to the telemetry service.
     *
     * This method is called periodically, once per content submission.
     *
     * @return
     */
    @Nonnull
    public abstract String createContent();

    /**
     * MIME type of the content sent to the telemetry service.
     *
     * @return MIME type
     */
    @Nonnull
    public String getContentType() {
        return "text/plain";
    }

    public static ExtensionList<Telemetry> all() {
        return ExtensionList.lookup(Telemetry.class);
    }

    @Extension
    public static class TelemetryReporter extends AsyncPeriodicWork {

        public TelemetryReporter() {
            super("telemetry collection");
        }

        @Override
        public long getRecurrencePeriod() {
            return TimeUnit.HOURS.toMillis(24);
        }

        @Override
        protected void execute(TaskListener listener) throws IOException, InterruptedException {
            if (UsageStatistics.DISABLED || !Jenkins.getInstance().isUsageStatisticsCollected()) {
                LOGGER.info("Collection of anonymous usage statistics is disabled, skipping telemetry collection and submission");
                return;
            }
            Telemetry.all().forEach(telemetry -> {
                if (telemetry.getStart().isAfter(LocalDate.now())) {
                    LOGGER.config("Skipping telemetry for '" + telemetry.getId() + "' as it is configured to start later");
                    return;
                }
                if (telemetry.getEnd().isBefore(LocalDate.now())) {
                    LOGGER.config("Skipping telemetry for '" + telemetry.getId() + "' as it is configured to end in the past");
                    return;
                }

                String data = "";
                try {
                    data = telemetry.createContent();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to build telemetry content for: " + telemetry.getDisplayName(), e);
                }

                String endpoint = BASE_ENDPOINT + telemetry.getId();

                try {
                    URL url = new URL(endpoint);
                    URLConnection conn = ProxyConfiguration.open(url);
                    if (!(conn instanceof HttpsURLConnection)) {
                        // TODO not sure this check is a good idea, but worst possible outcome seems to be losing data from some instances, so that's fine.
                        LOGGER.config("Refusing to communicate telemetry except via HTTPS: " + endpoint);
                        return;
                    }
                    HttpURLConnection http = (HttpURLConnection) conn;
                    http.setRequestProperty("Content-Type", telemetry.getContentType() + "; charset=utf-8");
                    http.setDoOutput(true);

                    try (OutputStreamWriter writer = new OutputStreamWriter(http.getOutputStream(), StandardCharsets.UTF_8)) {
                        writer.append(data);
                    }

                    LOGGER.config("Telemetry submission received response '" + http.getResponseCode() + " " + http.getResponseMessage() + "' for: " + telemetry.getId());
                } catch (MalformedURLException e) {
                    LOGGER.config("Malformed endpoint URL: " + endpoint + " for telemetry: " + telemetry.getId());
                } catch (IOException e) {
                    // deliberately low visibility, as temporary infra problems aren't a big deal and we'd
                    // rather have some unsuccessful submissions than admins opting out to clean up logs
                    LOGGER.log(Level.CONFIG, "Failed to submit telemetry: " + telemetry.getId() + " to: " + endpoint, e);
                }
            });
        }
    }

    // https://webhook.site is a nice stand-in for this during development; just needs to end in ? to submit the ID as query parameter
    private static final String BASE_ENDPOINT = "https://telemetry.jenkins.io/";

    private static final Logger LOGGER = Logger.getLogger(Telemetry.class.getName());
}
