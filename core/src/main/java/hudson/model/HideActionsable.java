package hudson.model;

import java.util.List;

public interface HideActionsable {

    List<Class<? extends Action>> hideActions();
}
