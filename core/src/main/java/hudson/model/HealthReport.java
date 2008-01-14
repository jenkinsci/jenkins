package hudson.model;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Represents health of something (typically project).
 * A number between 0-100.
 *
 * @author connollys
 * @since 1.115
 */
@ExportedBean(defaultVisibility=2) // this is always exported as a part of Job and never on its own, so start with 2.
public class HealthReport implements Serializable, Comparable<HealthReport> {
    private static final String HEALTH_OVER_80 = "health-80plus.gif";
    private static final String HEALTH_60_TO_79 = "health-60to79.gif";
    private static final String HEALTH_40_TO_59 = "health-40to59.gif";
    private static final String HEALTH_20_TO_39 = "health-20to39.gif";
    private static final String HEALTH_0_TO_19 = "health-00to19.gif";
    private static final String HEALTH_UNKNOWN = "empty.gif";

    /** The percentage health score (from 0 to 100 inclusive). */
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

    /** The health icon's tool-tip. */
    private String description;

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
    public HealthReport(int score, String iconUrl, String description) {
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
        this.description = description;
    }

    /**
     * Create a new HealthReport.
     *
     * @param score       The percentage health score (from 0 to 100 inclusive).
     * @param description The health icon's tool-tip.
     */
    public HealthReport(int score, String description) {
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
    public String getIconUrl() {
        return iconUrl;
    }

    /**
     * Get's the iconUrl relative to the hudson root url, for the correct size.
     * @param size The size, e.g. 32x32, 24x24 or 16x16.
     * @return The url relative to hudson's root url.
     */
    public String getIconUrl(String size) {
        if (iconUrl == null) {
            return Hudson.RESOURCE_PATH+"/images/" + size + "/" + HEALTH_UNKNOWN;
        }
        if (iconUrl.startsWith("/")) {
            return iconUrl.replace("/32x32/", "/" + size + "/");
        }
        return Hudson.RESOURCE_PATH+"/images/" + size + "/" + iconUrl;
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
        return description;
    }

    /**
     * Setter for property 'description'.
     *
     * @param description Value to set for property 'description'.
     */
    public void setDescription(String description) {
        this.description = description;
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

    /** {@inheritDoc} */
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
}
