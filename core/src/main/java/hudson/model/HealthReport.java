/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly
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

import com.thoughtworks.xstream.converters.UnmarshallingContext;
import hudson.diagnosis.OldDataMonitor;
import hudson.util.XStream2;
import jenkins.model.Jenkins;
import jenkins.util.NonLocalizable;
import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    // These are now 0-20, 21-40, 41-60, 61-80, 81+ but filenames unchanged for compatibility
    private static final String HEALTH_OVER_80 = "icon-health-80plus";
    private static final String HEALTH_61_TO_80 = "icon-health-60to79";
    private static final String HEALTH_41_TO_60 = "icon-health-40to59";
    private static final String HEALTH_21_TO_40 = "icon-health-20to39";
    private static final String HEALTH_0_TO_20 = "icon-health-00to19";

    private static final String HEALTH_OVER_80_IMG = "health-80plus.png";
    private static final String HEALTH_61_TO_80_IMG = "health-60to79.png";
    private static final String HEALTH_41_TO_60_IMG = "health-40to59.png";
    private static final String HEALTH_21_TO_40_IMG = "health-20to39.png";
    private static final String HEALTH_0_TO_20_IMG = "health-00to19.png";
    private static final String HEALTH_UNKNOWN_IMG = "empty.png";

    private static final Map<String, String> iconIMGToClassMap = new HashMap<String, String>();
    static {
        iconIMGToClassMap.put(HEALTH_OVER_80_IMG, HEALTH_OVER_80);
        iconIMGToClassMap.put(HEALTH_61_TO_80_IMG, HEALTH_61_TO_80);
        iconIMGToClassMap.put(HEALTH_41_TO_60_IMG, HEALTH_41_TO_60);
        iconIMGToClassMap.put(HEALTH_21_TO_40_IMG, HEALTH_21_TO_40);
        iconIMGToClassMap.put(HEALTH_0_TO_20_IMG, HEALTH_0_TO_20);
    }

    /**
     * The percentage health score (from 0 to 100 inclusive).
     */
    private int score;

    /**
     * Icon class.
     */
    private String iconClassName;

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
     * @deprecated since 2008-10-18. Use {@link #localizibleDescription}
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
     * @deprecated since 2008-10-18.
     *     Use {@link #HealthReport(int, String, org.jvnet.localizer.Localizable)}
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
        if (score <= 20) {
            this.iconClassName = HEALTH_0_TO_20;
        } else if (score <= 40) {
            this.iconClassName = HEALTH_21_TO_40;
        } else if (score <= 60) {
            this.iconClassName = HEALTH_41_TO_60;
        } else if (score <= 80) {
            this.iconClassName = HEALTH_61_TO_80;
        } else {
            this.iconClassName = HEALTH_OVER_80;
        }
        if (iconUrl == null) {
            if (score <= 20) {
                this.iconUrl = HEALTH_0_TO_20_IMG;
            } else if (score <= 40) {
                this.iconUrl = HEALTH_21_TO_40_IMG;
            } else if (score <= 60) {
                this.iconUrl = HEALTH_41_TO_60_IMG;
            } else if (score <= 80) {
                this.iconUrl = HEALTH_61_TO_80_IMG;
            } else {
                this.iconUrl = HEALTH_OVER_80_IMG;
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
     * @deprecated since 2008-10-18.
     *     Use {@link #HealthReport(int, org.jvnet.localizer.Localizable)}
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
        this(100, HEALTH_UNKNOWN_IMG, Messages._HealthReport_EmptyString());
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
     * Get health status icon class.
     *
     * @return The health status icon class.
     */
    @Exported
    public String getIconClassName() {
        return iconClassName;
    }

    /**
     * Get's the iconUrl relative to the hudson root url, for the correct size.
     *
     * @param size The size, e.g. 32x32, 24x24 or 16x16.
     * @return The url relative to hudson's root url.
     */
    public String getIconUrl(String size) {
        if (iconUrl == null) {
            return Jenkins.RESOURCE_PATH + "/images/" + size + "/" + HEALTH_UNKNOWN_IMG;
        }
        if (iconUrl.startsWith("/")) {
            return iconUrl.replace("/32x32/", "/" + size + "/");
        }
        return Jenkins.RESOURCE_PATH + "/images/" + size + "/" + iconUrl;
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
     */
    public static class ConverterImpl extends XStream2.PassthruConverter<HealthReport> {
        public ConverterImpl(XStream2 xstream) { super(xstream); }
        @Override protected void callback(HealthReport hr, UnmarshallingContext context) {
            // If we are being read back in from an older version
            if (hr.localizibleDescription == null) {
                hr.localizibleDescription = new NonLocalizable(hr.description == null ? "" : hr.description);
                OldDataMonitor.report(context, "1.256");
            }

            if (hr.iconClassName == null && hr.iconUrl != null && iconIMGToClassMap.containsKey(hr.iconUrl)) {
                hr.iconClassName = iconIMGToClassMap.get(hr.iconUrl);
            }
        }
    }
}
