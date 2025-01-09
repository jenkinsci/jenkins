package jenkins.model;

import hudson.model.ModelObject;
import hudson.model.Run;

public abstract class Detail implements ModelObject {

    private final Run<?, ?> object;

    public Detail(Run<?, ?> object) {
        this.object = object;
    }

    protected Run<?, ?> getObject() {
        return object;
    }

    /**
     * {@inheritDoc}
     */
    public abstract String getIconFileName();

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract String getDisplayName();

    /**
     * @return the grouping of the detail
     */
    public Group getGroup() {
        return Group.GENERIC;
    }

    /**
     * @return order in the group, zero is first, MAX_VALUE is any order
     */
    public int getOrder() {
        return Integer.MAX_VALUE;
    }
}
