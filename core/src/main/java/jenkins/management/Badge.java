/*
 * The MIT License
 *
 * Copyright (c) 2022, Markus Winter
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

package jenkins.management;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.diagnosis.OldDataMonitor;
import hudson.model.AdministrativeMonitor;
import hudson.model.ManagementLink;
import java.util.Locale;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 *  Definition of a badge that can be returned by a {@link ManagementLink} implementation.
 *  The badge is shown as a small overlay over the corresponding icon on the {@code Manage Jenkins} page,
 *  it can display additional information in a tooltip and change it's color depending on the severity.
 *
 *  <p>
 *  A badge mainly serves as a fast feedback for the corresponding management page.
 *  It could be used to just display some short status information or hint that some action can be taken.
 *  For example the badge on {@code Manage Plugins} shows information about the number of available updates
 *  and in its tooltip additionally how many updates contain incompatible changes or fix
 *  security vulnerabilities. It also changes its color when there are security fixes available.
 *
 *  <p>
 *  A badge might display the same information as an {@link AdministrativeMonitor}. While an {@link AdministrativeMonitor}
 *  can be disabled, a badge will always be shown. E.g. the badge of {@link OldDataMonitor.ManagementLinkImpl} always shows the number of old data entries.
 *
 *  <p>
 *  A badge can also be used in a {@code <l:task .../>} to show information on the right of the link in the sidepanel,
 *  e.g. to show number of available plugin updates.
 *
 *  @since 2.385
 */
@ExportedBean
public class Badge {

    private final String text;
    private final String tooltip;
    private final Severity severity;

    /**
     * Create a new Badge
     *
     * @param text  The text to be shown in the badge.
     *      Keep it short, ideally just a number. More than 6 or 7 characters do not look good. Avoid spaces as they will lead to line breaks.
     * @param tooltip  The tooltip to show for the badge.
     *      Do not include html tags.
     * @param severity  The severity of the badge (danger, warning, info)
     */
    public Badge(@NonNull String text, @NonNull String tooltip, @NonNull Severity severity) {
        this.text = text;
        this.tooltip = tooltip;
        this.severity = severity;
    }

    /**
     * The text to be shown in the badge.
     *
     * @return badge text
     */
    @Exported(visibility = 999)
    public String getText() {
        return text;
    }

    /**
     * The tooltip of the badge.
     *
     * @return tooltip
     */
    @Exported(visibility = 999)
    public String getTooltip() {
        return tooltip;
    }

    /**
     * The severity of the badge.
     * Influences the background color of the badge.
     *
     * @return severity as String
     */
    @Exported(visibility = 999)
    public String getSeverity() {
        return severity.toString().toLowerCase(Locale.US);
    }

    public enum Severity {
        DANGER, WARNING, INFO
    }
}
