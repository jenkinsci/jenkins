package jenkins.model.menu.event;

import org.kohsuke.stapler.export.ExportedBean;

/**
 * Represents an action in the app-bar.
 * This is a JavaScript API and is not currently extensible to new types.
 */
@ExportedBean
public sealed interface Event permits ConfirmationEvent, LinkEvent, JavaScriptEvent, SplitButtonEvent {
}
