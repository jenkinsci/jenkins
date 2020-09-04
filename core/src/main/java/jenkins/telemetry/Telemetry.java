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

import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.ProxyConfiguration;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
import hudson.model.UsageStatistics;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import net.sf.json.JSONObject;
import org.apache.commons.codec.digest.DigestUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.OutputStream;
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
 * Implementations should provide a {@code description.jelly} file with additional details about their purpose and
 * behavior which will be included in {@code help-usageStatisticsCollected.jelly} for {@link UsageStatistics}.
 *
 * @see <a href="https://jenkins.io/jep/214">JEP-214</a>
 *
 * @since 2.143
 */
public abstract class Telemetry implements ExtensionPoint {

    // https://webhook.site is a nice stand-in for this during development; just needs to end in ? to submit the ID as query parameter
    @Restricted(NoExternalUse.class)
    @VisibleForTesting
    static String ENDPOINT = SystemProperties.getString(Telemetry.class.getName() + ".endpoint", "https://uplink.jenkins.io/events");

    private static final Logger LOGGER = Logger.getLogger(Telemetry.class.getName());

    /**
     * ID of this collector, typically an alphanumeric string (and punctuation).
     *
     * Good IDs are globally unique and human readable (i.e. no UUIDs).
     *
     * For a periodically updated list of all public implementations, see https://jenkins.io/doc/developer/extensions/jenkins-core/#telemetry
     *
     * @return ID of the collector, never null or empty
     */
    @NonNull
    public String getId() {
        return getClass().getName();
    }

    /**
     * User friendly display name for this telemetry collector, ideally localized.
     *
     * @return display name, never null or empty
     */
    @NonNull
    public abstract String getDisplayName();

    /**
     * Start date for the collection.
     * Will be checked in Jenkins to not collect outside the defined time span.
     * This does not have to be precise enough for time zones to be a consideration.
     *
     * @return collection start date
     */
    @NonNull
    public abstract LocalDate getStart();

    /**
     * End date for the collection.
     * Will be checked in Jenkins to not collect outside the defined time span.
     * This does not have to be precise enough for time zones to be a consideration.
     *
     * @return collection end date
     */
    @NonNull
    public abstract LocalDate getEnd();

    /**
     * Returns the content to be sent to the telemetry service.
     *
     * This method is called periodically, once per content submission.
     *
     * @return The JSON payload, or null if no content should be submitted.
     */
    @CheckForNull
    public abstract JSONObject createContent();

    public static ExtensionList<Telemetry> all() {
        return ExtensionList.lookup(Telemetry.class);
    }

    /**
     * @since 2.147
     * @return whether to collect telemetry
     */
    public static boolean isDisabled() {
        if (UsageStatistics.DISABLED) {
            return true;
        }
        Jenkins jenkins = Jenkins.getInstanceOrNull();

        return jenkins == null || !jenkins.isUsageStatisticsCollected();
    }

    /**
     * Returns true iff we're in the time period during which this is supposed to collect data.
     * @return true iff we're in the time period during which this is supposed to collect data
     *
     * @since 2.202
     */
    public boolean isActivePeriod() {
        LocalDate now = LocalDate.now();
        return now.isAfter(getStart()) && now.isBefore(getEnd());
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
            if (isDisabled()) {
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

                JSONObject data = new JSONObject();
                try {
                    data = telemetry.createContent();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to build telemetry content for: '" + telemetry.getId() + "'", e);
                }

                if (data == null) {
                    LOGGER.log(Level.CONFIG, "Skipping telemetry for '" + telemetry.getId() + "' as it has no data");
                    return;
                }

                JSONObject wrappedData = new JSONObject();
                wrappedData.put("type", telemetry.getId());
                wrappedData.put("payload", data);
                String correlationId = ExtensionList.lookupSingleton(Correlator.class).getCorrelationId();
                wrappedData.put("correlator", DigestUtils.sha256Hex(correlationId + telemetry.getId()));

                try {
                    URL url = new URL(ENDPOINT);
                    URLConnection conn = ProxyConfiguration.open(url);
                    if (!(conn instanceof HttpURLConnection)) {
                        LOGGER.config("URL did not result in an HttpURLConnection: " + ENDPOINT);
                        return;
                    }
                    HttpURLConnection http = (HttpURLConnection) conn;
                    http.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    http.setDoOutput(true);

                    String body = wrappedData.toString();
                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.finest("Submitting JSON: " + body);
                    }

                    try (OutputStream out = http.getOutputStream();
                            OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                        writer.append(body);
                    }

                    LOGGER.config("Telemetry submission received response '" + http.getResponseCode() + " " + http.getResponseMessage() + "' for: " + telemetry.getId());
                } catch (MalformedURLException e) {
                    LOGGER.config("Malformed endpoint URL: " + ENDPOINT + " for telemetry: " + telemetry.getId());
                } catch (IOException e) {
                    // deliberately low visibility, as temporary infra problems aren't a big deal and we'd
                    // rather have some unsuccessful submissions than admins opting out to clean up logs
                    LOGGER.log(Level.CONFIG, "Failed to submit telemetry: " + telemetry.getId() + " to: " + ENDPOINT, e);
                }
            });
        }
    }
}
