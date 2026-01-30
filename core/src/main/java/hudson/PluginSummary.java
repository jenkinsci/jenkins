package hudson;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class PluginSummary {
    private final String shortName;
    private final String version;
    private final boolean enabled;

    public PluginSummary(PluginWrapper plugin) {
        this.shortName = plugin.getShortName();
        this.version = plugin.getVersion();
        this.enabled = plugin.isEnabled();
    }

    @Exported
    public String getShortName() {
        return shortName;
    }

    @Exported
    public String getVersion() {
        return version;
    }

    @Exported
    public boolean isEnabled() {
        return enabled;
    }
}
