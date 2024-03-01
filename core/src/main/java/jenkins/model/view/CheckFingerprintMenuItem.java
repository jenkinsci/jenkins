package jenkins.model.view;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.View;
import java.util.Collection;
import java.util.Set;
import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;
import jenkins.model.menu.Group;
import jenkins.model.menu.event.LinkAction;

public class CheckFingerprintMenuItem implements Action {

    @Override
    public String getDisplayName() {
        return "Check file fingerprint";
    }

    @Override
    public String getIconFileName() {
        return "symbol-fingerprint";
    }

    @Override
    public Group getGroup() {
        return Group.FIRST_IN_MENU;
    }

    @Override
    public jenkins.model.menu.event.Action getAction() {
        return LinkAction.of(Jenkins.get().getRootUrl() + "fingerprint");
    }

    @Extension
    public static class TransientActionFactoryImpl extends TransientActionFactory<View> {

        @Override
        public Class<View> type() {
            return View.class;
        }

        @Override
        public Collection<? extends Action> createFor(View target) {
            return Set.of(new CheckFingerprintMenuItem());
        }
    }
}
