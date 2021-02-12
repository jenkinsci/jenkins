package jenkins.management;

import hudson.model.AdministrativeMonitor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.ArrayList;
import java.util.List;

@Restricted(NoExternalUse.class)
public class AdministrativeMonitorsApiData {
    private final List<AdministrativeMonitor> monitorsList = new ArrayList<>();

    AdministrativeMonitorsApiData(List<AdministrativeMonitor> monitors) {
        monitorsList.addAll(monitors);
    }

    public List<AdministrativeMonitor> getMonitorsList() {
        return this.monitorsList;
    }

    public boolean hasActiveMonitors() {
        return !this.monitorsList.isEmpty();
    }
}
