/*
 * The MIT License
 *
 * Copyright 2021 Tim Jacomb.
 * Copyright 2023 Mark Waite.
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

package jenkins.monitor;

import static java.util.logging.Level.FINE;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.AdministrativeMonitor;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

/* Package protected so that it won't be visible outside this package */
class EndOfLifeAdminMonitor extends AdministrativeMonitor {

    /* URL of documentation that provides more details about operating system end of life */
    static final String DOCUMENTATION_URL = "https://www.jenkins.io/redirect/operating-system-end-of-life";

    /**
     * Unique identifier of end of life admin monitor.
     */
    private final String identifier;

    /**
     * Display name of end of life admin monitor.
     * Typically "End of life for (dependencyName)".
     */
    final String displayName;

    /**
     * Name of the dependency that will be end of life, like "Ubuntu 18.04"
     */
    final String dependencyName;

    /**
     * Date to begin displaying the end of life admin monitor, like 2023-01-31
     */
    final LocalDate beginDisplayDate;

    /**
     * Date that support ends, like 2023-05-31
     */
    final LocalDate endOfSupportDate;

    /**
     * URL with more information.
     */
    final String documentationURL = DOCUMENTATION_URL;

    /**
     * True if the dataPattern matched content of the file when constructor was
     * called. Assumes the content of the file is not changing while Jenkins is
     * running.
     */
    final boolean dataPatternMatched;

    /**
     * True if current date is after the end of support date
     */
    boolean unsupported;

    /* Each end of life admin monitor needs to be separately enabled and disabled */
    protected Boolean disabled;

    EndOfLifeAdminMonitor(@NonNull String identifier,
            @NonNull String dependencyName,
            @NonNull LocalDate beginDisplayDate,
            @NonNull LocalDate endOfSupportDate,
            @NonNull File dataFile,
            @NonNull Pattern dataPattern) {
        super(identifier);
        this.identifier = identifier;
        this.disabled = SystemProperties.getBoolean(identifier + ".disabled", false);
        this.dependencyName = dependencyName;
        this.displayName = "End of life for " + dependencyName;
        this.beginDisplayDate = beginDisplayDate;
        this.endOfSupportDate = endOfSupportDate;
        this.dataPatternMatched = patternMatched(dataFile, dataPattern);
    }

    private boolean patternMatched(File dataFile, Pattern dataPattern) {
        boolean matched = false;
        LOGGER.log(FINE, "Reading file {1} with monitor {0}", new Object[] {identifier, dataFile});
        if (dataFile.isFile()) {
            try (BufferedReader reader = new BufferedReader(Files.newBufferedReader(dataFile.toPath(), Charset.defaultCharset()))) {
                String line = reader.readLine();
                while (line != null) {
                    if (dataPattern.matcher(line).matches()) {
                        matched = true;
                        LOGGER.log(FINE, "{0} matched in file {1}", new Object[] {identifier, dataFile});
                        break;
                    }
                    line = reader.readLine();
                }
            } catch (IOException e) {
                LOGGER.log(FINE, "Exception reading file {1} in context of {0}", new Object[] {identifier, dataFile});
                matched = false;
            }
        }
        LOGGER.log(FINE, "Matched is {1} in context of {0}", new Object[] {identifier, matched});
        return matched;
    }

    @Override
    public boolean isActivated() {
        if (disabled) {
            LOGGER.log(FINE, "Not activated - disabled in {0}", identifier);
            return false;
        }
        if (!dataPatternMatched) {
            LOGGER.log(FINE, "Not activated - data pattern not matched - disabled in {0}", identifier);
            return false;
        }
        LocalDate now = LocalDate.now();
        if (!now.isAfter(beginDisplayDate)) {
            LOGGER.log(FINE, "Not activated - Date is not after {0} - disabled in {1}", new Object[] {beginDisplayDate, identifier});
            return false;
        }
        LOGGER.log(FINE, "Activated for date {0} in {1}", new Object[] {beginDisplayDate, identifier});
        return true;
    }

    /**
     * Depending on whether the user said "yes" or "no", send him to the right
     * place.
     */
    @Restricted(DoNotUse.class) // WebOnly
    @RequirePOST
    public HttpResponse doAct(@QueryParameter String no) throws IOException {
        if (no != null) { // dismiss
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            disable(true);
            LOGGER.log(FINE, "Disabled admin monitor {0}", displayName);
            return HttpResponses.forwardToPreviousPage();
        } else {
            LOGGER.log(FINE, "Enabled admin monitor {0}", displayName);
            return new HttpRedirect(documentationURL);
        }
    }

    static final Logger LOGGER = Logger.getLogger(EndOfLifeAdminMonitor.class.getName());
}
