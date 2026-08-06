package jenkins.model.menu.event;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * When clicked, a dialog will pop up for confirmation.
 * e.g. Delete item.
 */
@ExportedBean
@Restricted(Beta.class)
public final class ConfirmationEvent implements Event {

    private final String title;

    private final String description;

    private final String postTo;

    private ConfirmationEvent(String title, String description, String postTo) {
        this.title = title;
        this.description = description;
        this.postTo = postTo;
    }

    /**
     * Create a confirmation event.
     *
     * <p>The {@code title} is rendered as plain text in the dialog, any HTML
     * in it will be escaped.
     *
     * @param title title of the dialog, rendered as plain text.
     * @param postTo url that the action should be submitted to, e.g. {@code doDelete}.
     * @since 2.560
     */
    public static ConfirmationEvent of(String title, String postTo) {
        return new ConfirmationEvent(title, null, postTo);
    }

    /**
     * Create a confirmation event.
     *
     * <p>Both {@code title} and {@code description} are rendered as plain text in the
     * dialog, any HTML in either will be escaped, not interpreted.
     *
     * @param title title of the dialog, rendered as plain text.
     * @param description additional contextual information about what is being confirmed,
     *                    rendered as plain text.
     * @param postTo url that the action should be submitted to, e.g. {@code doDelete}.
     * @since 2.560
     */
    public static ConfirmationEvent of(String title, String description, String postTo) {
        return new ConfirmationEvent(title, description, postTo);
    }

    @Exported
    public String getTitle() {
        return title;
    }

    @Exported
    public String getDescription() {
        return description;
    }

    @Exported
    public String getPostTo() {
        return postTo;
    }
}
