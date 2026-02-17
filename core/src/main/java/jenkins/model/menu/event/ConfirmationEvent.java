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
     * Created a confirmation event.
     * @param title title of the dialog
     * @param description additional contextual information about what is being confirmed.
     * @param postTo url that it should be submitted to, e.g. deleteItem
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
