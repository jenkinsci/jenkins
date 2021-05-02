package jenkins.model;

import com.google.common.annotations.VisibleForTesting;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.ModifiableViewGroup;
import hudson.model.TransientViewActionFactory;
import hudson.model.View;
import hudson.model.ViewGroup;
import java.util.Collections;
import java.util.List;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

@Extension
@Restricted(NoExternalUse.class)
public class NewViewLink extends TransientViewActionFactory {

    @VisibleForTesting
    static final String ICON_FILE_NAME = "folder";
    @VisibleForTesting
    public static final String URL_NAME = "newView";

    @Override
    public List<Action> createFor(final View v) {
        // do not show the action if the viewgroup is not modifiable
        ViewGroup vg = v.getOwner();
        if (vg instanceof ModifiableViewGroup) {
            return Collections.singletonList(new NewViewLinkAction((ModifiableViewGroup)vg));
        }
        return Collections.emptyList();
    }

    private static class NewViewLinkAction implements Action {

        private ModifiableViewGroup target;

        private NewViewLinkAction(ModifiableViewGroup target) {
            this.target = target;
        }

        @Override
        public String getIconFileName() {
            if (hasPermission()) {
                return ICON_FILE_NAME;
            }
            return null;
        }

        @Override
        public String getDisplayName() {
            return Messages.NewViewLink_NewView();
        }

        @Override
        public String getUrlName() {
            // getUrl returns the path from the root (without the context and no leading slash)
            // we need to add the slash so that this is not relative to the current URL but to the context
            return "/" + target.getUrl() + URL_NAME;
        }

        private boolean hasPermission() {
            return target.hasPermission(View.CREATE);
        }

    }
}
