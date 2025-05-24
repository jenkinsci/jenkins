/*
 * The MIT License
 *
 * Copyright 2021 Tim Jacomb.
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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.security.Permission;
import java.io.IOException;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.NavigableMap;
import java.util.TreeMap;
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
@Symbol("javaVersionRecommendation")
@Deprecated(since = "TODO", forRemoval = true)
public class JavaVersionRecommendationAdminMonitor extends AdministrativeMonitor {

    /**
     * The list of supported Java long-term support (LTS) releases. The key is the {@link
     * Runtime.Version#feature() feature-release counter}. The value is the date the Jenkins project
     * drops support for that release, which must be before the date the Eclipse Temurin project
     * drops support for that release. This list must remain synchronized with the one in {@code
     * executable.Main}.
     *
     * <p>To add support for a Java version:
     *
     * <ul>
     *   <li>Update {@link #SUPPORTED_JAVA_VERSIONS}
     *   <li>Update {@code executable.Main#SUPPORTED_JAVA_VERSIONS}
     *   <li>Update the {@code Jenkinsfile} for core and core components
     *   <li>Update the {@code Jenkinsfile} for PCT
     *   <li>Update the {@code Jenkinsfile} for ATH
     *   <li>Update the archetype and the {@code Jenkinsfile} for critical plugins
     * </ul>
     *
     * @see <a href="https://endoflife.date/eclipse-temurin">Eclipse Temurin End of Life</a>
     */
    private static final NavigableMap<Integer, LocalDate> SUPPORTED_JAVA_VERSIONS;

    static {
        NavigableMap<Integer, LocalDate> supportedVersions = new TreeMap<>();
        supportedVersions.put(17, LocalDate.of(2026, 3, 31)); // Temurin: 2027-10-31
        supportedVersions.put(21, LocalDate.of(2027, 9, 30)); // Temurin: 2029-09-30
        SUPPORTED_JAVA_VERSIONS = Collections.unmodifiableNavigableMap(supportedVersions);
    }

    public JavaVersionRecommendationAdminMonitor() {
        super(getId());
    }

    /**
     * Compute the ID for the administrative monitor. The ID includes the Java version, EOL date,
     * and severity so that changes to the EOL date for a given Java version will invalidate
     * previous dismissals of the administrative monitor and so that users who decline to upgrade
     * after the first warning get a second warning when they are closer to the deadline.
     *
     * @return The computed ID.
     */
    private static String getId() {
        StringBuilder id = new StringBuilder();
        id.append(JavaVersionRecommendationAdminMonitor.class.getName());
        LocalDate endOfLife = getEndOfLife();
        if (endOfLife.isBefore(LocalDate.MAX)) {
            id.append('-');
            id.append(Runtime.version().feature());
            id.append('-');
            id.append(endOfLife);
            id.append('-');
            id.append(getSeverity());
        }
        return id.toString();
    }

    private static Boolean disabled = SystemProperties.getBoolean(JavaVersionRecommendationAdminMonitor.class.getName() + ".disabled", false);

    @Override
    public boolean isActivated() {
        return !disabled && getDeprecationPeriod().toTotalMonths() < 12;
    }

    @Override
    public String getDisplayName() {
        return Messages.JavaLevelAdminMonitor_DisplayName();
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
            return new HttpRedirect("https://jenkins.io/redirect/java-support/");
        }
    }

    @NonNull
    private static LocalDate getEndOfLife() {
        LocalDate endOfLife = SUPPORTED_JAVA_VERSIONS.get(Runtime.version().feature());
        return endOfLife != null ? endOfLife : LocalDate.MAX;
    }

    @NonNull
    private static Period getDeprecationPeriod() {
        return Period.between(LocalDate.now(), getEndOfLife());
    }

    @NonNull
    private static Severity getSeverity() {
        return getDeprecationPeriod().toTotalMonths() < 3 ? Severity.DANGER : Severity.WARNING;
    }

    /**
     * @return The current feature-release counter.
     * @see Runtime#version()
     */
    @Restricted(DoNotUse.class)
    public int getJavaVersion() {
        return Runtime.version().feature();
    }

    /**
     * @return The end of life date for the current Java version in the system default time zone.
     */
    @Restricted(DoNotUse.class)
    public Date getEndOfLifeAsDate() {
        return Date.from(getEndOfLife().atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /**
     * @return The severity of the administrative monitor, used to set the background color of the alert.
     */
    @Restricted(DoNotUse.class)
    public String getSeverityAsString() {
        return getSeverity().toString().toLowerCase(Locale.US);
    }

    private enum Severity {
        SUCCESS,
        INFO,
        WARNING,
        DANGER
    }
}
