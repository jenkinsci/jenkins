package jenkins.model.menu.event;

import hudson.model.Action;
import java.util.List;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public final class DropdownEvent implements Event {

    private final List<Action> actions;

    private DropdownEvent(List<Action> actions) {
        this.actions = actions;
    }

    public static DropdownEvent of(List<Action> actions) {
        return new DropdownEvent(actions);
    }

    @Exported
    public List<Action> getActions() {
        return actions;
    }
}
