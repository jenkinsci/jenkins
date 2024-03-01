package jenkins.model.menu;

import jenkins.management.Badge;
import jenkins.model.menu.event.Action;

public interface MenuItem {

    String getLabel();

    String getIcon();

    Group getGroup();

    Action getAction();

    default String getId() {
        return null;
    }

    default Semantic getSemantic() {
        return null;
    }

    default Badge getBadge() {
        return null;
    }
}
