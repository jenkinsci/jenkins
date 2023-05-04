package jenkins.widgets;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.widgets.Widget;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import jenkins.security.stapler.StaplerAccessibleType;
import jenkins.security.stapler.StaplerDispatchable;

/**
 * Classes implementing this interface can have widgets, and they can be accessed using relative urls "widget/widgetName"
 */
@StaplerAccessibleType
public interface HasWidgets {
    Logger LOGGER = Logger.getLogger(HasWidgets.class.getName());

    /**
     * @return the list of widgets attached to the object.
     */
    default List<Widget> getWidgets() {
        List<Widget> result = new ArrayList<>();
        WidgetFactory
                .factoriesFor(getClass(), Widget.class)
                .forEach(wf -> result.addAll(wf.createWidgetsFor(this)));
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns the named widget, or <code>null</code> if it does not exist.
     *
     * Defaults to iterating on widgets and filtering based on the defined <code>urlName</code>.
     *
     * @param name the name of the widget within the current context.
     * @return the named widget, or <code>null</code> if it does not exist.
     */
    @CheckForNull
    @StaplerDispatchable
    default Widget getWidget(String name) {
        if (name == null) {
            return null;
        }
        return getWidgets().stream().filter(w -> name.equals(w.getUrlName())).findFirst().orElse(null);
    }
}
