package jenkins.timemachine.pluginchange;

import hudson.util.VersionNumber;
import jenkins.timemachine.PluginSnapshot;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@Restricted(NoExternalUse.class)
public class Upgraded extends PluginChange {

    private VersionNumber from;
    private VersionNumber to;

    public Upgraded(PluginSnapshot from, PluginSnapshot to) {
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

    @Override
    public JSONObject toJSONObject() {
        JSONObject jsonObject = super.toJSONObject();
        jsonObject.put("from", from.toString());
        jsonObject.put("to", to.toString());
        return jsonObject;
    }
}
