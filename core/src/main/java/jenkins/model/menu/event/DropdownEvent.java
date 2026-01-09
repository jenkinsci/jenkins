package jenkins.model.menu.event;

import hudson.model.Action;
import java.util.List;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public final class DropdownEvent implements Event {

    private final Event event;

    private final List<Action> actions;

    private DropdownEvent(Event event, List<Action> actions) {
        if (event instanceof DropdownEvent) {
            throw new IllegalArgumentException("Cannot nest dropdown events");
        }

        this.event = event;
        this.actions = actions;
    }

    public static DropdownEvent of(Event event, List<Action> actions) {
        return new DropdownEvent(event, actions);
    }

    public static DropdownEvent of(List<Action> actions) {
        return new DropdownEvent(null, actions);
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
