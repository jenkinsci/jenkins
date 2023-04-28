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

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.security.Permission;
import java.io.IOException;
import java.text.DateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;

@Extension
@Restricted(NoExternalUse.class)
@Symbol("endOfLifeUbuntu1804AdminMonitor")
public class EndOfLifeAdminMonitor extends AdministrativeMonitor {

    /** Unique identifier of end of life admin monitor */
    private final String identifier;

    /** Display name of end of life admin monitor */
    private final String displayName;

    /** Date to begin displaying the end of life admin monitor */
    private final String beginDisplayDate;

    /** URL with more information */
    private final String documentationURL;

    /* Each end of life admin monitor needs to be separately enabled and disabled */
    private Boolean disabled;

    public EndOfLifeAdminMonitor() {
        super(EndOfLifeAdminMonitor.class.getName());
        this.identifier = "id_ubuntu_1804";
        this.displayName = "Display Ubuntu 18.04";
        this.beginDisplayDate = "2023-01-01";
        this.documentationURL = "https://www.jenkins.io/redirect/dependency-end-of-life";
        this.disabled = SystemProperties.getBoolean(EndOfLifeAdminMonitor.class.getName() + "." + identifier + ".disabled", false);
    }

    public EndOfLifeAdminMonitor(String identifier, String displayName, String beginDisplayDate, String documentationURL) {
        super(EndOfLifeAdminMonitor.class.getName());
        this.identifier = identifier; // TODO: Check that the identifier is unique
        this.displayName = displayName;
        this.beginDisplayDate = beginDisplayDate;
        this.documentationURL = documentationURL;
        this.disabled = SystemProperties.getBoolean(EndOfLifeAdminMonitor.class.getName() + "." + identifier + ".disabled", false);
    }

    @Override
    public boolean isActivated() {
        if (disabled) {
            return false;
        }
        DateFormat df = DateFormat.getDateInstance();
        LocalDate beginDisplay = null;
        try {
            beginDisplay = LocalDate.parse(beginDisplayDate);
        } catch (DateTimeParseException ex) {
            LOGGER.warning("Parse exception creating end of life admin monitor '" + identifier + "' for begin date " + beginDisplayDate);
            beginDisplay = LocalDate.now().minusDays(1);
        }
        LocalDate now = LocalDate.now();
        return now.isAfter(beginDisplay);
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getBeginDisplayDate() {
        return beginDisplayDate;
    }

    public String getDocumentationURL() {
        return documentationURL;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public Permission getRequiredPermission() {
        return Jenkins.SYSTEM_READ;
    }

    /**
     * Depending on whether the user said "yes" or "no", send him to the right place.
     */
    @Restricted(DoNotUse.class) // WebOnly
    @RequirePOST
    public HttpResponse doAct(@QueryParameter String no) throws IOException {
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
