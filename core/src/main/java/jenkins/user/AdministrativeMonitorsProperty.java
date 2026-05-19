package jenkins.user;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import hudson.model.Descriptor;
import hudson.model.User;
import hudson.model.UserProperty;
import hudson.model.UserPropertyDescriptor;
import hudson.model.userproperty.UserPropertyCategory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * User property to track which administrative monitors a user has dismissed.
 *
 * @since TODO
 */
@Restricted(NoExternalUse.class)
public class AdministrativeMonitorsProperty extends UserProperty {

    private static final Logger LOGGER = Logger.getLogger(AdministrativeMonitorsProperty.class.getName());
    private final Set<String> dismissedMonitors = new HashSet<>();

    @DataBoundConstructor
    public AdministrativeMonitorsProperty() {
    }

    @Override
    public UserProperty reconfigure(StaplerRequest2 req, JSONObject json) throws Descriptor.FormException {
        if (json == null) {
            return this;
        }
        JSONArray monitors = json.optJSONArray("administrativeMonitor");
        synchronized (dismissedMonitors) {
            for (AdministrativeMonitor am : AdministrativeMonitor.all()) {
                boolean disable;
                if (monitors != null) {
                    disable = !monitors.contains(am.id);
                } else {
                    disable = !am.id.equals(json.optString("administrativeMonitor"));
                }
                if (disable) {
                    dismissedMonitors.add(am.id);
                } else {
                    dismissedMonitors.remove(am.id);
                }
            }
        }
        return this;
    }

    public boolean isMonitorEnabled(String monitorId) {
        synchronized (dismissedMonitors) {
            return !dismissedMonitors.contains(monitorId);
        }
    }

    public boolean isMonitorGloballyDisabled(String monitorId) {
        return Jenkins.get().getDisabledAdministrativeMonitors().contains(monitorId);
    }

    public void disableMonitor(String monitorId, boolean enabled) throws IOException {
        synchronized (dismissedMonitors) {
            if (enabled) {
                dismissedMonitors.add(monitorId);
            } else {
                dismissedMonitors.remove(monitorId);
            }
        }
        user.save();
    }

    @NonNull
    public static AdministrativeMonitorsProperty get(@NonNull User user) {
        AdministrativeMonitorsProperty property = user.getProperty(AdministrativeMonitorsProperty.class);
        if (property == null) {
            property = new AdministrativeMonitorsProperty();
            try {
                user.addProperty(property);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to save user " + user.getId() + " after adding AdministrativeMonitorsProperty", e);
            }
        }
        return property;
    }

    public List<AdministrativeMonitor> getMonitors() {
        synchronized (dismissedMonitors) {
            List<AdministrativeMonitor> monitors = new ArrayList<>(AdministrativeMonitor.all());
            monitors.sort((m1, m2) -> m1.getDisplayName().compareTo(m2.getDisplayName()));
            return monitors;
        }
    }

    @Extension
    @Symbol("administrativeMonitors")
    public static final class DescriptorImpl extends UserPropertyDescriptor {

        @NonNull
        @Override
        public String getDisplayName() {
            return "Administrative Monitors";
        }

        @Override
        public UserProperty newInstance(User user) {
            return new AdministrativeMonitorsProperty();
        }

        @NonNull
        @Override
        public UserPropertyCategory getUserPropertyCategory() {
            User user = User.current();
            if (user != null) {
                if (!AdministrativeMonitor.hasPermissionToDisplay()) {
                    return UserPropertyCategory.get(UserPropertyCategory.Invisible.class);
                }
            }
            return UserPropertyCategory.get(UserPropertyCategory.Preferences.class);
        }

    }
}
