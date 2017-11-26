package jenkins.timemachine;

import hudson.util.VersionNumber;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class PluginSnapshot {

    private String pluginId;
    private VersionNumber version;
    private boolean enabled;

    public String getPluginId() {
        return pluginId;
    }

    public @Nonnull PluginSnapshot setPluginId(@Nonnull String pluginId) {
        this.pluginId = pluginId;
        return this;
    }

    public VersionNumber getVersion() {
        return version;
    }

    public @Nonnull PluginSnapshot setVersion(@Nonnull VersionNumber version) {
        this.version = version;
        return this;
    }

    public @Nonnull PluginSnapshot setVersion(@Nonnull String version) {
        this.version = new VersionNumber(version);
        return this;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public PluginSnapshot setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PluginSnapshot that = (PluginSnapshot) o;

        if (enabled != that.enabled) return false;
        if (pluginId != null ? !pluginId.equals(that.pluginId) : that.pluginId != null) return false;
        return version != null ? version.equals(that.version) : that.version == null;
    }

    @Override
    public int hashCode() {
        int result = pluginId != null ? pluginId.hashCode() : 0;
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + (enabled ? 1 : 0);
        return result;
    }
}
