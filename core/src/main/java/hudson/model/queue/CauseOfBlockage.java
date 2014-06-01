package hudson.model.queue;

import hudson.console.ModelHyperlinkNote;
import hudson.model.Queue.Task;
import hudson.model.Node;
import hudson.model.Messages;
import hudson.model.Label;
import hudson.model.TaskListener;
import hudson.slaves.Cloud;
import org.jvnet.localizer.Localizable;

/**
 * If something is blocked/vetoed, this object represents why.
 *
 * <p>
 * Originally, this is added for {@link Task} stuck in the queue, but since then the use of this
 * has expanded beyond queues.
 *
 * <h2>View</h2>
 * <tt>summary.jelly</tt> should do one-line HTML rendering to be used showing the cause
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
     * Report a line to the listener about this cause.
     */
    public void print(TaskListener listener) {
        listener.getLogger().println(getShortDescription());
    }

    /**
     * Obtains a simple implementation backed by {@link Localizable}.
     */
    public static CauseOfBlockage fromMessage(final Localizable l) {
        return new CauseOfBlockage() {
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

        public String getShortDescription() {
            String name = (node.toComputer() != null) ? node.toComputer().getDisplayName() : node.getDisplayName();
            return Messages.Queue_NodeOffline(name);
        }
        
        @Override
        public void print(TaskListener listener) {
            listener.getLogger().println(
                Messages.Queue_NodeOffline(ModelHyperlinkNote.encodeTo(node)));
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

        public String getShortDescription() {
            if (label.isEmpty()) {
                return Messages.Queue_LabelHasNoNodes(label.getName());
            } else {
                return Messages.Queue_AllNodesOffline(label.getName());
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

        public String getShortDescription() {
            String name = (node.toComputer() != null) ? node.toComputer().getDisplayName() : node.getDisplayName();
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

        public String getShortDescription() {
            return Messages.Queue_WaitingForNextAvailableExecutorOn(label.getName());
        }
    }
}
