package jenkins.model;

import hudson.model.ModelObject;

public abstract class Detail implements ModelObject {

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
