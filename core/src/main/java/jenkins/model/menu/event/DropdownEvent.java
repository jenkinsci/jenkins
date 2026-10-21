package jenkins.model.menu.event;

import hudson.model.Action;
import java.util.List;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.Beta;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * A dropdown button of additional actions.
 */
@ExportedBean
@Restricted(Beta.class)
public sealed class DropdownEvent implements Event permits SplitButtonEvent {

    private final List<Action> actions;

    DropdownEvent(List<Action> actions) {
        this.actions = actions.stream().filter(e -> e.getIconFileName() != null).toList();
    }

    /**
     * Creates a dropdown event
     * @param actions actions to render in the overflow
     */
    public static DropdownEvent of(List<Action> actions) {
        return new DropdownEvent(actions);
    }

    @Exported
    public List<Action> getActions() {
        return actions;
    }
}
