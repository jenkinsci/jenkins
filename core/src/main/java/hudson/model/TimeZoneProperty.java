package hudson.model;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.userproperty.UserPropertyCategory;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;
import java.text.DateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * A UserProperty that allows a user to specify a time zone for displaying time.
 */
@Restricted(NoExternalUse.class)
public class TimeZoneProperty extends UserProperty {
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

    public void setTimeZoneName(@CheckForNull String timeZoneName) {
        this.timeZoneName = timeZoneName;
    }

    @CheckForNull
    public String getTimeZoneName() {
        return timeZoneName;
    }

    @Extension @Symbol("timezone")
    public static class DescriptorImpl extends UserPropertyDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.TimeZoneProperty_DisplayName();
        }

        @Override
        public String getDescription() {
            return Messages.TimeZoneProperty_Description();
        }

        @Override
        public UserProperty newInstance(User user) {
            return new TimeZoneProperty();
        }

        @Override
        public synchronized void load() {
            super.load();
        }

        @Override
        public synchronized void save() {
            super.save();
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        public ListBoxModel doFillTimeZoneNameItems(@AncestorInPath User user) {
            String userTimezone = user != null ? forUser(user) : forCurrentUser();
            ListBoxModel items = new ListBoxModel();
            items.add(Messages.TimeZoneProperty_DisplayDefaultTimeZone(), "");
            for (String id : TimeZone.getAvailableIDs()) {
                if (id.equalsIgnoreCase(userTimezone)) {
                    items.add(new Option(id, id, true));
                } else {
                    items.add(id);
                }
            }
            return items;
        }

        public FormValidation doCheckTimeZoneName(@QueryParameter String timeZoneName) {
            Date now = new Date();
            if (Util.fixEmpty(timeZoneName) == null) {
                return FormValidation.ok(Messages.TimeZoneProperty_current_time_in_(TimeZone.getDefault().getDisplayName(), DateFormat.getDateTimeInstance().format(now)));
            } else {
                DateFormat localTime = DateFormat.getDateTimeInstance();
                localTime.setTimeZone(TimeZone.getTimeZone(timeZoneName));
                return FormValidation.ok(Messages.TimeZoneProperty_current_time_on_server_in_in_proposed_di(TimeZone.getDefault().getDisplayName(), DateFormat.getDateTimeInstance().format(now), localTime.format(now)));
            }
        }

        @Override
        public @NonNull UserPropertyCategory getUserPropertyCategory() {
            return UserPropertyCategory.get(UserPropertyCategory.Account.class);
        }
    }

    @CheckForNull
    public static String forCurrentUser() {
        final User current = User.current();
        if (current == null) {
            return null;
        }
        return forUser(current);
    }

    @CheckForNull
    private static String forUser(User user) {
        TimeZoneProperty tzp = user.getProperty(TimeZoneProperty.class);
        if (tzp.timeZoneName == null || tzp.timeZoneName.isEmpty()) {
            return null;
        }

        TimeZone tz = TimeZone.getTimeZone(tzp.timeZoneName);
        if (!tz.getID().equals(tzp.timeZoneName)) {
            //TimeZone.getTimeZone returns GMT on invalid time zone so
            //warn the user if the time zone returned is different from
            //the one they specified.
            LOGGER.log(Level.WARNING, "Invalid user time zone {0} for {1}", new Object[]{tzp.timeZoneName, user.getId()});
            return null;
        }

        return tz.getID();
    }
}
