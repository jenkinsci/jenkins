package jenkins.views;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.model.userproperty.UserPropertyCategory;
import hudson.views.ViewsTabBar;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

@Restricted(NoExternalUse.class)
public class ViewsTabBarUserProperty extends UserProperty {

    private ViewsTabBar viewsTabBar;

    @DataBoundConstructor
    public ViewsTabBarUserProperty() {
    }

    @DataBoundSetter
    public void setViewsTabBar(ViewsTabBar viewsTabBar) {
        this.viewsTabBar = viewsTabBar;
    }

    @CheckForNull
    public ViewsTabBar getViewsTabBar() {
        return viewsTabBar;
    }

    @Extension
    @Symbol("viewsTabBar")
    public static class DescriptorImpl extends UserPropertyDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.ViewsTabBarUserProperty_DisplayName();
        }

        @Override
        public UserProperty newInstance(User user) {
            return new ViewsTabBarUserProperty();
        }

        @Override
        public @NonNull UserPropertyCategory getUserPropertyCategory() {
            return UserPropertyCategory.get(UserPropertyCategory.Preferences.class);
        }
    }
}
