/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly
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
package hudson.model;

import org.jvnet.localizer.Localizable;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.*;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Represents health of something (typically project).
 * A number between 0-100.
 *
 * @author connollys
 * @since 1.115
 */
@ExportedBean(defaultVisibility = 2)
// this is always exported as a part of Job and never on its own, so start with 2.
public class HealthReport implements Serializable, Comparable<HealthReport> {
    private static final String HEALTH_OVER_80 = "health-80plus.gif";
    private static final String HEALTH_60_TO_79 = "health-60to79.gif";
    private static final String HEALTH_40_TO_59 = "health-40to59.gif";
    private static final String HEALTH_20_TO_39 = "health-20to39.gif";
    private static final String HEALTH_0_TO_19 = "health-00to19.gif";
    private static final String HEALTH_UNKNOWN = "empty.gif";

    /**
     * The percentage health score (from 0 to 100 inclusive).
     */
    private int score;

    /**
     * The path to the icon corresponding to this health score or <code>null</code> to use the default icon
     * corresponding to the current health score.
     * <p/>
     * If the path begins with a '/' then it will be the absolute path, otherwise the image is assumed to be in one of
     * <code>/images/16x16/</code>, <code>/images/24x24/</code> or <code>/images/32x32/</code> depending on the icon
     * size selected by the user.
     */
    private String iconUrl;

    /**
     * Recover the health icon's tool-tip when deserializing.
     *
     * @deprecated use {@link #localizibleDescription}
     */
    @Deprecated
    private transient String description;

    /**
     * The health icon's tool-tip.
     */
    private Localizable localizibleDescription;

    /**
     * Create a new HealthReport.
     *
     * @param score       The percentage health score (from 0 to 100 inclusive).
     * @param iconUrl     The path to the icon corresponding to this {@link Action}'s health or <code>null</code> to
     *                    display the default icon corresponding to the current health score.
     *                    <p/>
     *                    If the path begins with a '/' then it will be the absolute path, otherwise the image is
     *                    assumed to be in one of <code>/images/16x16/</code>, <code>/images/24x24/</code> or
     *                    <code>/images/32x32/</code> depending on the icon size selected by the user.
     *                    When calculating the url to display for absolute paths, the getIconUrl(String) method
     *                    will replace /32x32/ in the path with the appropriate size.
     * @param description The health icon's tool-tip.
     * @deprecated use {@link #HealthReport(int, String, org.jvnet.localizer.Localizable)}
     */
    @Deprecated
    public HealthReport(int score, String iconUrl, String description) {
        this(score, iconUrl, new NonLocalizable(description));
    }

    /**
     * Create a new HealthReport.
     *
     * @param score       The percentage health score (from 0 to 100 inclusive).
     * @param iconUrl     The path to the icon corresponding to this {@link Action}'s health or <code>null</code> to
     *                    display the default icon corresponding to the current health score.
     *                    <p/>
     *                    If the path begins with a '/' then it will be the absolute path, otherwise the image is
     *                    assumed to be in one of <code>/images/16x16/</code>, <code>/images/24x24/</code> or
     *                    <code>/images/32x32/</code> depending on the icon size selected by the user.
     *                    When calculating the url to display for absolute paths, the getIconUrl(String) method
     *                    will replace /32x32/ in the path with the appropriate size.
     * @param description The health icon's tool-tip.
     */
    public HealthReport(int score, String iconUrl, Localizable description) {
        this.score = score;
        if (iconUrl == null) {
            if (score < 20) {
                this.iconUrl = HEALTH_0_TO_19;
            } else if (score < 40) {
                this.iconUrl = HEALTH_20_TO_39;
            } else if (score < 60) {
                this.iconUrl = HEALTH_40_TO_59;
            } else if (score < 80) {
                this.iconUrl = HEALTH_60_TO_79;
            } else {
                this.iconUrl = HEALTH_OVER_80;
            }
        } else {
            this.iconUrl = iconUrl;
        }
        this.description = null;
        setLocalizibleDescription(description);
    }

    /**
     * Create a new HealthReport.
     *
     * @param score       The percentage health score (from 0 to 100 inclusive).
     * @param description The health icon's tool-tip.
     * @deprecated use {@link #HealthReport(int, org.jvnet.localizer.Localizable)}
     */
    @Deprecated
    public HealthReport(int score, String description) {
        this(score, null, description);
    }

    /**
     * Create a new HealthReport.
     *
     * @param score       The percentage health score (from 0 to 100 inclusive).
     * @param description The health icon's tool-tip.
     */
    public HealthReport(int score, Localizable description) {
        this(score, null, description);
    }

    /**
     * Create a new HealthReport.
     */
    public HealthReport() {
        this(100, HEALTH_UNKNOWN, "");
    }

    /**
     * Getter for property 'score'.
     *
     * @return The percentage health score (from 0 to 100 inclusive).
     */
    @Exported
    public int getScore() {
        return score;
    }

    /**
     * Setter for property 'score'.
     *
     * @param score Value to set for property 'score'.
     */
    public void setScore(int score) {
        this.score = score;
    }

    /**
     * Getter for property 'iconUrl'.
     *
     * @return Value for property 'iconUrl'.
     */
    @Exported
    public String getIconUrl() {
        return iconUrl;
    }

    /**
     * Get's the iconUrl relative to the hudson root url, for the correct size.
     *
     * @param size The size, e.g. 32x32, 24x24 or 16x16.
     * @return The url relative to hudson's root url.
     */
    public String getIconUrl(String size) {
        if (iconUrl == null) {
            return Hudson.RESOURCE_PATH + "/images/" + size + "/" + HEALTH_UNKNOWN;
        }
        if (iconUrl.startsWith("/")) {
            return iconUrl.replace("/32x32/", "/" + size + "/");
        }
        return Hudson.RESOURCE_PATH + "/images/" + size + "/" + iconUrl;
    }

    /**
     * Setter for property 'iconUrl'.
     *
     * @param iconUrl Value to set for property 'iconUrl'.
     */
    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    /**
     * Getter for property 'description'.
     *
     * @return Value for property 'description'.
     */
    @Exported
    public String getDescription() {
        return getLocalizableDescription().toString();
    }

    /**
     * Setter for property 'description'.
     *
     * @param description Value to set for property 'description'.
     */
    public void setDescription(String description) {
        setLocalizibleDescription(new NonLocalizable(description));
    }

    /**
     * Getter for property 'localizibleDescription'.
     *
     * @return Value for property 'localizibleDescription'.
     */
    public Localizable getLocalizableDescription() {
        return localizibleDescription;
    }

    /**
     * Setter for property 'localizibleDescription'.
     *
     * @param localizibleDescription Value to set for property 'localizibleDescription'.
     */
    public void setLocalizibleDescription(Localizable localizibleDescription) {
        this.localizibleDescription = localizibleDescription;
    }

    /**
     * Getter for property 'aggregatedReports'.
     *
     * @return Value for property 'aggregatedReports'.
     */
    public List<HealthReport> getAggregatedReports() {
        return Collections.emptyList();
    }

    /**
     * Getter for property 'aggregateReport'.
     *
     * @return Value for property 'aggregateReport'.
     */
    public boolean isAggregateReport() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public int compareTo(HealthReport o) {
        return (this.score < o.score ? -1 : (this.score == o.score ? 0 : 1));
    }

    /**
     * Utility method to find the report with the lowest health.
     */
    public static HealthReport min(HealthReport a, HealthReport b) {
        if (a == null && b == null) return null;
        if (a == null) return b;
        if (b == null) return a;
        if (a.compareTo(b) <= 0) return a;
        return b;
    }

    /**
     * Utility method to find the report with the highest health.
     */
    public static HealthReport max(HealthReport a, HealthReport b) {
        if (a == null && b == null) return null;
        if (a == null) return b;
        if (b == null) return a;
        if (a.compareTo(b) >= 0) return a;
        return b;
    }

    /**
     * Fix deserialization of older data.
     *
     * @return this.
     */
    private Object readResolve() {
        // If we are being read back in from an older version
        if (localizibleDescription == null) {
            localizibleDescription = new NonLocalizable(description == null ? "" : description);
        }
        return this;
    }

    /**
     * In order to provide backwards compatibility, we use this crazy class to fake out localization.
     */
    private static class NonLocalizable extends Localizable {
        /**
         * The string that we don't know how to localize
         */
        private final String nonLocalizable;

        /**
         * Creates a non-localizable string.
         *
         * @param nonLocalizable the string.
         */
        public NonLocalizable(String nonLocalizable) {
            super(null, null);
            this.nonLocalizable = nonLocalizable;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString(Locale locale) {
            return nonLocalizable;    //To change body of overridden methods use File | Settings | File Templates.
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return nonLocalizable;    //To change body of overridden methods use File | Settings | File Templates.
        }
    }

}
