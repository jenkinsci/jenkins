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

import static java.util.logging.Level.INFO;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.AdministrativeMonitor;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
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

    /** Unique identifier of end of life admin monitor, like "ubuntu_1804" */
    final String identifier;

    /** Display name of end of life admin monitor, like "Ubuntu 18.04 end of life" */
    final String displayName;

    /** Name of the dependency, like "Ubuntu 18.04" */
    final String dependencyName;

    /** Date to begin displaying the end of life admin monitor, like 2023-05-31 */
    final LocalDate beginDisplayDate;

    /** URL with more information, like "https://www.jenkins.io/redirect/dependency-end-of-life" */
    final String documentationURL;

    /** True if the dataPattern matched content of the file when constructor was called */
    final boolean dataPatternMatched;

    /* Each end of life admin monitor needs to be separately enabled and disabled */
    private Boolean disabled;

    public EndOfLifeAdminMonitor(@NonNull String identifier,
                                 @NonNull String dependencyName,
                                 @NonNull LocalDate beginDisplayDate,
                                 @NonNull String documentationURL,
                                 @NonNull File dataFile,
                                 @NonNull Pattern dataPattern) {
        super(EndOfLifeAdminMonitor.class.getName());
        this.identifier = identifier;
        this.dependencyName = dependencyName;
        this.displayName = dependencyName + " end of life";
        this.beginDisplayDate = beginDisplayDate;
        this.documentationURL = documentationURL;
        this.disabled = SystemProperties.getBoolean(EndOfLifeAdminMonitor.class.getName() + "." + identifier + ".disabled", false);
        boolean matched = false;
        LOGGER.log(INFO, "Reading file {0}", dataFile);
        if (dataFile.isFile()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
                String line = reader.readLine();
                while (line != null) {
                    if (dataPattern.matcher(line).matches()) {
                        matched = true;
                        LOGGER.log(INFO, "Matched in file {0}", dataFile);
                        break;
                    }
                    line = reader.readLine();
                }
            } catch (IOException e) {
                LOGGER.log(INFO, "Exception reading file {0}", dataFile);
                matched = false;
            }
        }
        LOGGER.log(INFO, "Matched is {0}", matched);
        this.dataPatternMatched = matched;
    }

    @Override
    public boolean isActivated() {
        LOGGER.fine("Checking is activated");
        if (disabled || !dataPatternMatched) {
            LOGGER.fine("disabled or data pattern not matched");
            return false;
        }
        LocalDate now = LocalDate.now();
        if (!now.isAfter(beginDisplayDate)) {
            LOGGER.log(INFO, "Date is not after {0}", beginDisplayDate);
            return false;
        }
        LOGGER.log(INFO, "Activated for date {0}", beginDisplayDate);
        return true;
    }

    /**
     * Depending on whether the user said "yes" or "no", send him to the right place.
     */
    @Restricted(DoNotUse.class) // WebOnly
    @RequirePOST
    public HttpResponse doAct(@QueryParameter String no) throws IOException {
        LOGGER.log(INFO, "Called doAct");
        if (no != null) { // dismiss
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            disable(true);
            return HttpResponses.forwardToPreviousPage();
        } else {
            return new HttpRedirect(documentationURL);
        }
    }

    static final Logger LOGGER = Logger.getLogger(EndOfLifeAdminMonitor.class.getName());
}
