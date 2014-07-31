package jenkins.widgets;

import hudson.Extension;
import hudson.widgets.Widget;
import jenkins.model.Jenkins;

/**
 * Show the default build queue.
 *
 * A plugin may remove this from {@link Jenkins#getWidgets()} and swap in their own.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.514
 */
@Extension(ordinal=200) // historically this was the top most widget
public class BuildQueueWidget extends Widget {

}
