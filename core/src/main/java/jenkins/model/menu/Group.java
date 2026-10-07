package jenkins.model.menu;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Predefined ordinals for grouping menu items.
 */
@ExportedBean
public class Group {

    private final int order;

    private Group(int order) {
        if (order < 0) {
            throw new RuntimeException("Action orders cannot be less than 0");
        }

        this.order = order;
    }

    /**
     * Primary actions shown first in the app bar.
     */
    public static final Group FIRST_IN_APP_BAR = of(0);

    /**
     * Important actions shown in the app bar.
     */
    public static final Group IN_APP_BAR = of(1);

    /**
     * Last action to show up in the app bar.
     */
    public static final Group LAST_IN_APP_BAR = of(2);

    /**
     * Important actions for the menu
     */
    public static final Group FIRST_IN_MENU = of(3);

    /**
     * Default group for actions shown in the menu.
     */
    public static final Group IN_MENU = of(100);

    /**
     * Last action to show up in the menu. Should be used for deleting objects.
     */
    public static final Group LAST_IN_MENU = of(Integer.MAX_VALUE);

    /**
     * Create a custom group with the given order.
     * @param customOrder the order of the group.
     * @return the group
     */
    public static Group of(int customOrder) {
        return new Group(customOrder);
    }

    @Exported
    public int getOrder() {
        return order;
    }
}
