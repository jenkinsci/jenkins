package jenkins.management;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 *  Definition of a badge that is displayed on the Manage Jenkins page.
 *  The badge is shown as a small overlay over the corresponding icon.
 */
public class Badge {

    private final String text;
    private final String tooltip;
    private final Severity severity;

    /**
     * Create a new Badge
     *
     * @param text  The text to be shown in the badge.
     *      Keep it short, ideally just a number. More than 6 or 7 characters do not look good. Do not put in blanks
     *      as this might lead to line breaks.
     * @param tooltip  The tooltip to show for the badge.
     *      Do not include html tags.
     * @param severity  The severity of the badge (danger, warning, info)
     */
    public Badge(@NonNull String text, String tooltip, Severity severity) {
        this.text = text;
        this.tooltip = tooltip;
        this.severity = severity;
    }

    /**
     * The text to be shown in the badge.
     *
     * @return badge text
     */
    public String getText() {
        return text;
    }

    /**
     * The tooltip of the badge.
     *
     * @return tooltip
     */
    public String getTooltip() {
        return tooltip;
    }

    /**
     * The severity of the badge.
     * Influences the background color of the badge.
     *
     * @return severity
     */
    public Severity getSeverity() {
        return severity;
    }

    public enum Severity {
        danger, warning, info
    }
}
