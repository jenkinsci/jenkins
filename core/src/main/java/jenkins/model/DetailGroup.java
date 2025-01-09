package jenkins.model;

public class DetailGroup {

    private final int order;

    private DetailGroup(int order) {
        if (order < 0) {
            throw new RuntimeException("Orders cannot be less than 0");
        }

        this.order = order;
    }

    public static DetailGroup SCM = of(0);

    public static DetailGroup GENERAL = of(Integer.MAX_VALUE);

    // TODO - expose this yay or nay?
    private static DetailGroup of(int customOrder) {
        return new DetailGroup(customOrder);
    }

    public int getOrder() {
        return order;
    }
}
