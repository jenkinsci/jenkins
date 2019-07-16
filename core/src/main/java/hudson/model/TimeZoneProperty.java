package hudson.model;

import hudson.Extension;
import hudson.Util;
import hudson.util.FormValidation;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

public class TimeZoneProperty extends UserProperty implements Saveable, Action {
    /**
     * Name of the primary view defined by the user.
     * {@code null} means that the View is not defined.
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

    private boolean useLocalTime = true;

    public void save() throws IOException {
        user.save();
    }

    @Extension
    @Symbol("useLocalTime")
    public static class DescriptorImpl extends UserPropertyDescriptor {

        @Override
        public UserProperty newInstance(User user) {
            return new TimeZoneProperty();
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }

    public static String forCurrentUser() {
        final User current = User.current();
        if (current == null) {
            return "";
        }
        TimeZoneProperty tzp = current.getProperty(TimeZoneProperty.class);
        return tzp.timeZoneName;
    }

    public FormValidation doViewExistsCheck(@QueryParameter String value, @QueryParameter boolean exists) {
        String view = Util.fixEmpty(value);
        return FormValidation.error("HERE");
    }

    ///// Action methods /////
    public String getDisplayName() {
        return Messages.MyViewsProperty_DisplayName();
    }

    public String getIconFileName() {
        return "user.png";
    }

    public String getUrlName() {
        return "stuff";
    }
}
