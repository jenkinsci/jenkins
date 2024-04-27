package jenkins.model.menu.event;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public final class ConfirmationEvent implements Event {

    private final String title;

    private final String description;

    private final String postTo;

    private ConfirmationEvent(String title, String description, String postTo) {
        this.title = title;
        this.description = description;
        this.postTo = postTo;
    }

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
