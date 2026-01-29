package jenkins.model.menu.event;

import hudson.model.Action;
import java.util.List;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * A primary button with a dropdown of additional actions.
 */
@ExportedBean
public final class SplitButtonEvent implements Event {

    private final Event event;

    private final List<Action> actions;

    private SplitButtonEvent(Event event, List<Action> actions) {
        if (event instanceof SplitButtonEvent) {
            throw new IllegalArgumentException("Cannot nest dropdown events");
        }

        this.event = event;
        this.actions = actions;
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

    @Exported
    public List<Action> getActions() {
        return actions;
    }
}
