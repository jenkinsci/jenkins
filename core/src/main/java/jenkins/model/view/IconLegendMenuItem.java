package jenkins.model.view;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.View;
import java.util.Collection;
import java.util.Set;
import jenkins.model.TransientActionFactory;
import jenkins.model.menu.Group;
import jenkins.model.menu.event.DoNothingAction;

public class IconLegendMenuItem implements Action {

    @Override
    public String getId() {
        return "button-icon-legend";
    }

    @Override
    public String getDisplayName() {
        return "Icon legend";
    }

    @Override
    public String getIconFileName() {
        return "symbol-information-circle";
    }

    @Override
    public Group getGroup() {
        return Group.of(200);
    }

    @Override
    public boolean isVisibleInContextMenu() {
        return false;
    }

    @Override
    public jenkins.model.menu.event.Action getAction() {
        return new DoNothingAction();
    }

    @Extension
    public static class TransientActionFactoryImpl extends TransientActionFactory<View> {

        @Override
        public Class<View> type() {
            return View.class;
        }

        @Override
        public Collection<? extends Action> createFor(View target) {
            return Set.of(new IconLegendMenuItem());
        }
    }
}
