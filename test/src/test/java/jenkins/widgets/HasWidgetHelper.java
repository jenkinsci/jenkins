package jenkins.widgets;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.widgets.Widget;
import java.util.Optional;

/**
 * Helper methods that could be in {@link HasWidgets} but there is currently no use case for them except for tests.
 */
public class HasWidgetHelper {
    @NonNull
    public static Optional<Widget> getWidget(@NonNull HasWidgets hasWidgets, @NonNull Class<? extends Widget> type) {
        return hasWidgets.getWidgets().stream().filter(w -> type.isAssignableFrom(w.getClass())).findFirst();
    }
}
