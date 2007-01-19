package hudson.maven;

/**
 * Version independent name of a Maven project.
 * 
 * @author Kohsuke Kawaguchi
 */
public class ModuleName {
    public final String groupId;
    public final String artifactId;

    public ModuleName(String groupId, String artifactId) {
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    /**
     * Returns the "groupId:artifactId" form.
     */
    public String toString() {
        return groupId+':'+artifactId;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PluginName that = (PluginName) o;

        return artifactId.equals(that.artifactId)
            && groupId.equals(that.groupId);

    }

    public int hashCode() {
        int result;
        result = groupId.hashCode();
        result = 31 * result + artifactId.hashCode();
        return result;
    }
}
