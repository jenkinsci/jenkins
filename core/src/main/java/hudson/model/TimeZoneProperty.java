package hudson.model;

import hudson.Extension;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.TimeZone;

public class TimeZoneProperty extends UserProperty implements Saveable {
    /**
     * Time Zone ID defined by the user.
     * {@code null} means that the time zone is not defined.
     */
    @CheckForNull
    private String timeZoneName;

    @DataBoundConstructor
    public TimeZoneProperty(@CheckForNull String timeZoneName) {
        this.timeZoneName = timeZoneName;
    }

    private TimeZoneProperty() {
        this(null);
    }

    public void save() throws IOException {
        user.save();
    }

    @Extension
    @Symbol("timeZone")
    public static class DescriptorImpl extends UserPropertyDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.TimeZoneProperty_DisplayName();
        }

        @Override
        public UserProperty newInstance(User user) {
            return new TimeZoneProperty();
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        public ListBoxModel doFillTimeZoneNameItems() {
            String current = forCurrentUser();
            ListBoxModel items = new ListBoxModel();
            items.add(""); //default
            for (String id : TimeZone.getAvailableIDs()) {
                if (id.equalsIgnoreCase(current)) {
                    items.add(new Option(id, id, true));
                } else {
                    items.add(id);
                }
            }
            Messages.TimeZoneProperty_DisplayName();
            return items;
        }
    }

    public static String forCurrentUser() {
        final User current = User.current();
        if (current == null) {
            return null;
        }

        TimeZoneProperty tzp = current.getProperty(TimeZoneProperty.class);
        if(tzp.timeZoneName == null || tzp.timeZoneName.isEmpty()) {
            return null;
        }

        return tzp.timeZoneName;
    }
}
