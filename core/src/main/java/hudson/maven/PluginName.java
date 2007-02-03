package hudson.maven;

import org.apache.maven.plugin.descriptor.PluginDescriptor;

/**
 * Identifier of a specific version of a Maven plugin
 * that consists of groupId, artifactId, and version.
 *
 * @author Kohsuke Kawaguchi
 */
public final class PluginName {
    public final String groupId;
    public final String artifactId;
    public final String version;

    public PluginName(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public PluginName(PluginDescriptor pd) {
        this(pd.getGroupId(), pd.getArtifactId(), pd.getVersion());
    }

    /**
     * Returns the "groupId:artifactId:version" form.
     */
    public String toString() {
        return groupId+':'+artifactId+':'+version;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PluginName that = (PluginName) o;

        return artifactId.equals(that.artifactId)
            && groupId.equals(that.groupId)
            && version.equals(that.version);

    }

    public int hashCode() {
        int result;
        result = groupId.hashCode();
        result = 31 * result + artifactId.hashCode();
        result = 31 * result + version.hashCode();
        return result;
    }

    public boolean matches(String groupId, String artifactId) {
        return this.groupId.equals(groupId) && this.artifactId.equals(artifactId);
    }
}
