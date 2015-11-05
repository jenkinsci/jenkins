package jenkins.diagnostics;

import com.google.common.collect.ImmutableList;
import hudson.Extension;
import hudson.PluginWrapper;
import hudson.model.AdministrativeMonitor;
import jenkins.model.Jenkins;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * Fires off when we have any pinned plugins that's blocking upgrade from the bundled version.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class PinningIsBlockingBundledPluginMonitor extends AdministrativeMonitor {
    @Inject
    Jenkins jenkins;

    private List<PluginWrapper> offenders;

    @Override
    public boolean isActivated() {
        return !getOffenders().isEmpty();
    }

    private void compute() {
        List<PluginWrapper> offenders = new ArrayList<PluginWrapper>();
        for (PluginWrapper p : jenkins.pluginManager.getPlugins()) {
            if (p.isPinningForcingOldVersion())
                offenders.add(p);
        }
        this.offenders = ImmutableList.copyOf(offenders);
    }

    public List<PluginWrapper> getOffenders() {
        if (offenders==null)
            compute();
        return offenders;
    }
}
