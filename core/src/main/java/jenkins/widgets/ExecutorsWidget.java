package jenkins.widgets;

import hudson.Extension;
import hudson.widgets.Widget;
import jenkins.model.Jenkins;

/**
 * The default executors widget.
 *
 * A plugin may remove this from {@link Jenkins#getWidgets()} and swap in their own.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.514
 */
@Extension(ordinal=100) // historically this was above normal widgets and below BuildQueueWidget
public class ExecutorsWidget extends Widget {
}
