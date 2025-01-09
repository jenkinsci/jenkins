package jenkins.model;

public class Group {

    private final int order;

    private Group(int order) {
        if (order < 0) {
            throw new RuntimeException("Orders cannot be less than 0");
        }

        this.order = order;
    }

    public static Group SCM = of(0);

    public static Group GENERIC = of(Integer.MAX_VALUE);

    public static Group of(int customOrder) {
        return new Group(customOrder);
    }

    public int getOrder() {
        return order;
    }
}
