package hudson.util;

import hudson.model.Queue;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.ResourceList;

import java.io.IOException;

/**
 * Convenient base class for {@link Queue.Task} decorators.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class QueueTaskFilter implements Queue.Task {
    /**
     * Delegation target.
     */
    protected volatile Queue.Task delegate;

    protected QueueTaskFilter(Queue.Task delegate) {
        this.delegate = delegate;
    }

    protected QueueTaskFilter() {
    }

    public Label getAssignedLabel() {
        return delegate.getAssignedLabel();
    }

    public Node getLastBuiltOn() {
        return delegate.getLastBuiltOn();
    }

    public boolean isBuildBlocked() {
        return delegate.isBuildBlocked();
    }

    public String getWhyBlocked() {
        return delegate.getWhyBlocked();
    }

    public String getName() {
        return delegate.getName();
    }

    public String getFullDisplayName() {
        return delegate.getFullDisplayName();
    }

    public long getEstimatedDuration() {
        return delegate.getEstimatedDuration();
    }

    public Queue.Executable createExecutable() throws IOException {
        return delegate.createExecutable();
    }

    public void checkAbortPermission() {
        delegate.checkAbortPermission();
    }

    public boolean hasAbortPermission() {
        return delegate.hasAbortPermission();
    }

    public String getDisplayName() {
        return delegate.getDisplayName();
    }

    public ResourceList getResourceList() {
        return delegate.getResourceList();
    }

    // Queue.Task hashCode and equals need to be implemented to provide value equality semantics
    public abstract int hashCode();

    public abstract boolean equals(Object obj);
}
