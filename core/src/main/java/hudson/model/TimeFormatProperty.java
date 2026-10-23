package hudson.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.userproperty.UserPropertyCategory;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Allows users to display build history times in 24-hour format independently of their locale.
 */
@Restricted(NoExternalUse.class)
public class TimeFormatProperty extends UserProperty {
    private final boolean use24HourTime;

    @DataBoundConstructor
    public TimeFormatProperty(boolean use24HourTime) {
        this.use24HourTime = use24HourTime;
    }

    public boolean isUse24HourTime() {
        return use24HourTime;
    }

    public static boolean forCurrentUser() {
        User user = User.current();
        return user != null && user.getProperty(TimeFormatProperty.class).isUse24HourTime();
    }

    @Extension
    @Symbol("timeFormat")
    public static class DescriptorImpl extends UserPropertyDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.TimeFormatProperty_DisplayName();
        }

        @Override
        public UserProperty newInstance(User user) {
            return new TimeFormatProperty(false);
        }

        @NonNull
        @Override
        public UserPropertyCategory getUserPropertyCategory() {
            return UserPropertyCategory.get(UserPropertyCategory.Preferences.class);
        }
    }
}
