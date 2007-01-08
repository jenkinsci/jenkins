package hudson.maven;

import org.apache.maven.plugin.descriptor.PluginDescriptor;

/**
 * Identifier of an artifact (like jar) in Maven,
 * that consists of groupId, artifactId, and version.
 *
 *
 * @author Kohsuke Kawaguchi
 */
public final class Name {
    public final String groupId;
    public final String artifactId;
    public final String version;

    public Name(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public Name(PluginDescriptor pd) {
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

        Name that = (Name) o;

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
}
