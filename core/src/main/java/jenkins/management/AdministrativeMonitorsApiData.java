package jenkins.management;

import hudson.model.AdministrativeMonitor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.util.ArrayList;
import java.util.List;

@Restricted(NoExternalUse.class)
public class AdministrativeMonitorsApiData {
    private final ArrayList<AdministrativeMonitor> monitorsList = new ArrayList<AdministrativeMonitor>();

    AdministrativeMonitorsApiData(List<AdministrativeMonitor> monitors) {
        for (AdministrativeMonitor monitor : monitors) {
            monitorsList.add(monitor);
        }
    }

    public ArrayList<AdministrativeMonitor> getMonitorsList() {
        return this.monitorsList;
    }
}