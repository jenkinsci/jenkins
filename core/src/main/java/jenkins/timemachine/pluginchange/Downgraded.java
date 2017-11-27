package jenkins.timemachine.pluginchange;

import hudson.util.VersionNumber;
import jenkins.timemachine.PluginSnapshot;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@Restricted(NoExternalUse.class)
public class Downgraded extends PluginChange {

    private VersionNumber from;
    private VersionNumber to;

    public Downgraded(PluginSnapshot from, PluginSnapshot to) {
        super(to);
        this.from = from.getVersion();
        this.to = to.getVersion();
    }

    public VersionNumber getFrom() {
        return from;
    }

    public VersionNumber getTo() {
        return to;
    }
}
