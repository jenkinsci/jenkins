package jenkins.widgets;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.View;
import hudson.widgets.Widget;
import java.util.Collection;
import jenkins.model.Jenkins;

/**
 * Add widgets annotated with @Extension, or added manually to Jenkins via <code>Jenkins.get().getWidgets().add(...)</code>
 */
@Extension
public final class JenkinsWidgetFactory extends WidgetFactory<View> {
    @Override
    public Class<View> type() {
        return View.class;
    }

    @NonNull
    @Override
    public Collection<? extends Widget> createFor(@NonNull View target) {
        return Jenkins.get().getWidgets();
    }
}
