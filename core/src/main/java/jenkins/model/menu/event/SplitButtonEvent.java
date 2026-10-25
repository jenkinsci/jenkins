package jenkins.model.menu.event;

import hudson.model.Action;
import java.util.List;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * A primary button with a dropdown of additional actions.
 */
@ExportedBean
@Restricted(Beta.class)
public final class SplitButtonEvent extends DropdownEvent {

    private final Event event;

    private SplitButtonEvent(Event event, List<Action> actions) {
        super(actions);

        if (event instanceof DropdownEvent) {
            throw new IllegalArgumentException("Cannot nest dropdown events");
        }

        this.event = event;
    }

    /**
     * Creates a split button event
     * @param event the primary action in the split button
     * @param actions actions to render in the overflow
     */
    public static SplitButtonEvent of(Event event, List<Action> actions) {
        return new SplitButtonEvent(event, actions);
    }

    @Exported
    public Event getEvent() {
        return event;
    }
}
