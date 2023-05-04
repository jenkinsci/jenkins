package jenkins.widgets;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.ComputerSet;
import hudson.model.Queue;
import hudson.model.View;
import hudson.widgets.Widget;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;


public class BuildQueueWidget extends Widget {
    @NonNull
    private List<Queue.Item> queueItems;

    private boolean filtered;

    public BuildQueueWidget(@NonNull List<Queue.Item> queueItems) {
        this(queueItems, false);
    }

    public BuildQueueWidget(@NonNull List<Queue.Item> queueItems, boolean filtered) {
        this.queueItems = new ArrayList<>(queueItems);
        this.filtered = filtered;
    }

    @NonNull
    @SuppressWarnings("unused") // stapler
    public List<Queue.Item> getQueueItems() {
        return queueItems;
    }

    @SuppressWarnings("unused") // stapler
    public boolean isFiltered() {
        return filtered;
    }

    @Extension(ordinal = 200) @Symbol("buildQueue") // historically this was the top most widget
    public static final class ViewFactoryImpl extends WidgetFactory<View> {
        @Override
        public Class type() {
            return View.class;
        }

        @NonNull
        @Override
        public Collection<? extends Widget> createFor(@NonNull View target) {
            return List.of(new BuildQueueWidget(target.getQueueItems(), target.isFilterQueue()));
        }
    }

    @Extension(ordinal = 200) @Symbol("buildQueue") // historically this was the top most widget
    public static final class ComputerSetFactoryImpl extends WidgetFactory<ComputerSet> {
        @Override
        public Class type() {
            return ComputerSet.class;
        }

        @NonNull
        @Override
        public Collection<? extends Widget> createFor(@NonNull ComputerSet target) {
            return List.of(new BuildQueueWidget(List.of(Jenkins.get().getQueue().getItems())));
        }
    }
}
