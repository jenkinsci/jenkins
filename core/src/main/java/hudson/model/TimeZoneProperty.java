package hudson.model;

import hudson.Extension;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.TimeZone;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * A UserProperty that allows a user to specify a time zone for displaying time.
 *
 * @author Nathan Grubb
 */
public class TimeZoneProperty extends UserProperty implements Saveable {
    /**
     * Time Zone ID defined by the user.
     * {@code null} means that the time zone is not defined.
     */
    @CheckForNull
    private String timeZoneName;

    private static final Logger LOGGER = Logger.getLogger(TimeZoneProperty.class.getName());

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
    @Symbol("timezone")
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

    @Nullable
    public static String forCurrentUser() {
        final User current = User.current();
        if (current == null) {
            return null;
        }

        TimeZoneProperty tzp = current.getProperty(TimeZoneProperty.class);
        if(tzp.timeZoneName == null || tzp.timeZoneName.isEmpty()) {
            return null;
        }

        TimeZone tz = TimeZone.getTimeZone(tzp.timeZoneName);
        if (tz.getID() != tzp.timeZoneName) {
            //TimeZone.getTimeZone returns GMT on invalid time zone so
            //warn the user if the time zone returned is different from
            //the one they specified.
            LOGGER.log(Level.WARNING, "Invalid user time zone {0} defaulting to {1} for {2}", new Object[]{tzp.timeZoneName, tz.getID(), current.getId()});
        }

        return tz.getID();
    }
}
