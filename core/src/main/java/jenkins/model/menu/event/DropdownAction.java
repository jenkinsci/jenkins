package jenkins.model.menu.event;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public final class DropdownAction implements Action {

    private final List<hudson.model.Action> actions;

    private DropdownAction(List<hudson.model.Action> actions) {
        this.actions = actions;
    }

    public static DropdownAction of(hudson.model.Action... actions) {
        return new DropdownAction(Arrays.stream(actions).collect(Collectors.toList()));
    }

    @Exported(inline = true)
    public List<hudson.model.Action> getActions() {
        return actions;
    }
}
