package jenkins.model.menu.event;

import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public sealed interface Event permits ConfirmationEvent, DropdownEvent, JavaScriptEvent, LinkEvent {
}
