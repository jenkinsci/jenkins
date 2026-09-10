package jenkins.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.model.userproperty.UserPropertyCategory;
import hudson.util.ListBoxModel;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * User property that stores the preferred date and time format.
 *
 * <p>
 * This property allows users to control how timestamps are rendered in the Jenkins UI.
 * The format can either follow the browser's locale (the default behavior) or be
 * explicitly set to a specific representation such as 12-hour, 24-hour, or ISO-8601.
 * </p>
 *
 */
public class DateTimePreference extends UserProperty {

    private final String timeFormat;

    @DataBoundConstructor
    public DateTimePreference(String timeFormat) {
        this.timeFormat = timeFormat == null ? "auto" : timeFormat;
    }

    @SuppressWarnings("unused")
    public String getTimeFormat() {
        return timeFormat;
    }

    @Extension
    @Symbol("dateTimePreference")
    public static final class DescriptorImpl extends UserPropertyDescriptor {

        @Override
        public UserProperty newInstance(User user) {
            return new DateTimePreference("auto");
        }

        @Override
        public @NonNull String getDisplayName() {
            return "Date and Time Format";
        }

        @Override
        public @NonNull UserPropertyCategory getUserPropertyCategory() {
            return UserPropertyCategory.get(UserPropertyCategory.Preferences.class);
        }

        @Override
        public String getId() {
            return DateTimePreference.class.getName();
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillTimeFormatItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("Auto (Browser Default)", "auto");
            items.add("12-hour (6:05 PM)", "12h");
            items.add("24-hour (18:05)", "24h");
            items.add("ISO-8601 (2025-01-30 18:05)", "iso8601");
            return items;
        }
    }
}
