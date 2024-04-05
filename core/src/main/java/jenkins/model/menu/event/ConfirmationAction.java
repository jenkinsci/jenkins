package jenkins.model.menu.event;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public final class ConfirmationAction implements Action {

    private final String title;

    private final String description;

    private final String postTo;

    private ConfirmationAction(String title, String description, String postTo) {
        this.title = title;
        this.description = description;
        this.postTo = postTo;
    }

    public static ConfirmationAction of(String title, String description, String postTo) {
        return new ConfirmationAction(title, description, postTo);
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
