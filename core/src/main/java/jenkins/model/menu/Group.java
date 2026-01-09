package jenkins.model.menu;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class Group {

    private final int order;

    private Group(int order) {
        if (order < 0) {
            throw new RuntimeException("Action orders cannot be less than 0");
        }

        this.order = order;
    }

    public static Group FIRST_IN_APP_BAR = of(0);

    public static Group IN_APP_BAR = of(1);

    public static Group LAST_IN_APP_BAR = of(2);

    public static Group FIRST_IN_MENU = of(3);

    public static Group IN_MENU = of(100);

    public static Group LAST_IN_MENU = of(Integer.MAX_VALUE);

    public static Group of(int customOrder) {
        return new Group(customOrder);
    }

    @Exported
    public int getOrder() {
        return order;
    }
}
