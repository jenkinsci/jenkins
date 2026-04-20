package hudson.model.queue;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.console.ModelHyperlinkNote;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Messages;
import hudson.model.Node;
import hudson.model.Queue.Task;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import java.util.Objects;
import org.jvnet.localizer.Localizable;

/**
 * If something is blocked/vetoed, this object represents why.
 *
 * <p>
 * Originally, this is added for {@link Task} stuck in the queue, but since then the use of this
 * has expanded beyond queues.
 *
 * <h2>View</h2>
 * {@code summary.jelly} should do one-line HTML rendering to be used showing the cause
 * to the user. By default it simply renders {@link #getShortDescription()} text.
 *
 * <p>
 * For queues, this is used while rendering the "build history" widget.
 *
 *
 * @since 1.330
 */
public abstract class CauseOfBlockage {
    /**
     * Human readable description of why the build is blocked.
     */
    public abstract String getShortDescription();

    /**
     * @return {@code true} if the blockage is fatal and the item should be removed from the queue.
     * @since 2.532
     */
    public boolean isFatal() {
        return false;
    }

    /**
     * Report a line to the listener about this cause.
     */
    public void print(TaskListener listener) {
        listener.getLogger().println(getShortDescription());
    }

    /**
     * Obtains a simple implementation backed by {@link Localizable}.
     */
    public static CauseOfBlockage fromMessage(@NonNull final Localizable l) {
        Objects.requireNonNull(l);
        return new CauseOfBlockage() {
            @Override
            public String getShortDescription() {
                return l.toString();
            }
        };
    }

    @Override public String toString() {
        return getShortDescription();
    }

    /**
     * Marker interface to indicates that we can reasonably expect
     * that adding a suitable executor/node will resolve this blockage.
     *
     * Primarily this is used by {@link Cloud} to see if it should
     * consider provisioning new node.
     *
     * @since 1.427
     */
    interface NeedsMoreExecutor {}

    public static CauseOfBlockage createNeedsMoreExecutor(Localizable l) {
        return new NeedsMoreExecutorImpl(l);
    }

    private static final class NeedsMoreExecutorImpl extends CauseOfBlockage implements NeedsMoreExecutor {
        private final Localizable l;

        private NeedsMoreExecutorImpl(Localizable l) {
            this.l = l;
        }

        @Override
        public String getShortDescription() {
            return l.toString();
        }
    }

    /**
     * Build is blocked because a node is offline.
     */
    public static final class BecauseNodeIsOffline extends CauseOfBlockage implements NeedsMoreExecutor {
        public final Node node;

        public BecauseNodeIsOffline(Node node) {
            this.node = node;
        }

        @Override
        public String getShortDescription() {
            String name = node.toComputer() != null ? node.toComputer().getDisplayName() : node.getDisplayName();
            return Messages.Queue_NodeOffline(name);
        }

        @Override
        public void print(TaskListener listener) {
            listener.getLogger().println(
                Messages.Queue_NodeOffline(ModelHyperlinkNote.encodeTo(node)));
        }
    }

    /**
     * Build is blocked because a node (or its retention strategy) is not accepting tasks.
     * @since 2.37
     */
    public static final class BecauseNodeIsNotAcceptingTasks extends CauseOfBlockage implements NeedsMoreExecutor {

        public final Node node;

        public BecauseNodeIsNotAcceptingTasks(Node node) {
            this.node = node;
        }

        @Override
        public String getShortDescription() {
            Computer computer = node.toComputer();
            String name = computer != null ? computer.getDisplayName() : node.getDisplayName();
            return Messages.Node_BecauseNodeIsNotAcceptingTasks(name);
        }

        @Override
        public void print(TaskListener listener) {
            listener.getLogger().println(
                Messages.Node_BecauseNodeIsNotAcceptingTasks(ModelHyperlinkNote.encodeTo(node)));
        }

    }

    /**
     * Build is blocked because all the nodes that match a given label is offline.
     */
    public static final class BecauseLabelIsOffline extends CauseOfBlockage implements NeedsMoreExecutor {
        public final Label label;

        public BecauseLabelIsOffline(Label l) {
            this.label = l;
        }

        @Override
        public String getShortDescription() {
            if (label.isEmpty()) {
                return Messages.Queue_LabelHasNoNodes(label.getName());
            } else {
                return Messages.Queue_AllNodesOffline(label.getName());
            }
        }

        @Override
        public void print(TaskListener listener) {
            if (label.isEmpty()) {
                listener.getLogger().println(Messages.Queue_LabelHasNoNodes(ModelHyperlinkNote.encodeTo(label)));
            } else {
                listener.getLogger().println(Messages.Queue_AllNodesOffline(ModelHyperlinkNote.encodeTo(label)));
            }
        }

    }

    /**
     * Build is blocked because a node is fully busy
     */
    public static final class BecauseNodeIsBusy extends CauseOfBlockage implements NeedsMoreExecutor {
        public final Node node;

        public BecauseNodeIsBusy(Node node) {
            this.node = node;
        }

        @Override
        public String getShortDescription() {
            String name = node.toComputer() != null ? node.toComputer().getDisplayName() : node.getDisplayName();
            return Messages.Queue_WaitingForNextAvailableExecutorOn(name);
        }

        @Override
        public void print(TaskListener listener) {
            listener.getLogger().println(Messages.Queue_WaitingForNextAvailableExecutorOn(ModelHyperlinkNote.encodeTo(node)));
        }
    }

    /**
     * Build is blocked because everyone that matches the specified label is fully busy
     */
    public static final class BecauseLabelIsBusy extends CauseOfBlockage implements NeedsMoreExecutor {
        public final Label label;

        public BecauseLabelIsBusy(Label label) {
            this.label = label;
        }

        @Override
        public String getShortDescription() {
            return Messages.Queue_WaitingForNextAvailableExecutorOn(label.getName());
        }

        @Override
        public void print(TaskListener listener) {
            listener.getLogger().println(Messages.Queue_WaitingForNextAvailableExecutorOn(ModelHyperlinkNote.encodeTo(label)));
        }

    }
}
