package jenkins.widgets;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.ComputerSet;
import hudson.model.View;
import hudson.widgets.Widget;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import jenkins.model.IComputer;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;

/**
 * The default executors widget.
 *
 * A plugin may remove this from {@link Jenkins#getWidgets()} and swap in their own.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.514
 */
public class ExecutorsWidget extends Widget {
    private final String ownerUrl;
    private final List<IComputer> computers;

    public ExecutorsWidget(@NonNull String ownerUrl, @NonNull List<? extends IComputer> computers) {
        this.ownerUrl = ownerUrl;
        this.computers = new ArrayList<>(computers);
    }

    @Override
    protected String getOwnerUrl() {
        return ownerUrl;
    }

    public List<? extends IComputer> getComputers() {
        return Collections.unmodifiableList(computers);
    }

    @Extension(ordinal = 100) @Symbol("executors") // historically this was above normal widgets and below BuildQueueWidget
    public static final class ViewFactoryImpl extends WidgetFactory<View, ExecutorsWidget> {
        @Override
        public Class<View> type() {
            return View.class;
        }

        @Override
        public Class<ExecutorsWidget> widgetType() {
            return ExecutorsWidget.class;
        }

        @NonNull
        @Override
        public Collection<ExecutorsWidget> createFor(@NonNull View target) {
            return List.of(new ExecutorsWidget(target.getUrl(), target.getComputers()));
        }
    }

    @Extension(ordinal = 100) @Symbol("executorsComputer") // historically this was above normal widgets and below BuildQueueWidget
    public static final class ComputerFactoryImpl extends WidgetFactory<Computer, ExecutorsWidget> {
        @Override
        public Class<Computer> type() {
            return Computer.class;
        }

        @Override
        public Class<ExecutorsWidget> widgetType() {
            return ExecutorsWidget.class;
        }

        @NonNull
        @Override
        public Collection<ExecutorsWidget> createFor(@NonNull Computer target) {
            return List.of(new ExecutorsWidget(target.getUrl(), List.of(target)));
        }
    }

    @Extension(ordinal = 100) @Symbol("executorsComputerSet") // historically this was above normal widgets and below BuildQueueWidget
    public static final class ComputerSetFactoryImpl extends WidgetFactory<ComputerSet, ExecutorsWidget> {
        @Override
        public Class<ComputerSet> type() {
            return ComputerSet.class;
        }

        @Override
        public Class<ExecutorsWidget> widgetType() {
            return ExecutorsWidget.class;
        }

        @NonNull
        @Override
        public Collection<ExecutorsWidget> createFor(@NonNull ComputerSet target) {
            return List.of(new ExecutorsWidget("computer/", new ArrayList<>(target.getComputers())));
        }
    }
}
