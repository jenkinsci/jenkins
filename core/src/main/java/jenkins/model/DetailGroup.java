package jenkins.model;

/**
 * Represents a group for categorizing {@link Detail}, each with an associated order.
 */
public class DetailGroup {

    private final int order;

    private DetailGroup(int order) {
        if (order < 0) {
            throw new RuntimeException("Orders cannot be less than 0");
        }

        this.order = order;
    }

    public static DetailGroup SCM = new DetailGroup(0);

    public static DetailGroup GENERAL = new DetailGroup(Integer.MAX_VALUE);

    public int getOrder() {
        return order;
    }
}
