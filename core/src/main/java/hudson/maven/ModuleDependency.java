package hudson.maven;

import org.apache.maven.project.MavenProject;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.Extension;

import java.io.Serializable;

import hudson.Functions;

/**
 * group id + artifact id + version.
 *
 * @author Kohsuke Kawaguchi
 * @see ModuleName
 */
public final class ModuleDependency implements Serializable {
    public final String groupId;
    public final String artifactId;
    public final String version;

    public ModuleDependency(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        if(version==null)   version=UNKNOWN;
        this.version = version;
    }

    public ModuleDependency(ModuleName name, String version) {
        this(name.groupId,name.artifactId,version);
    }

    public ModuleDependency(org.apache.maven.model.Dependency dep) {
        this(dep.getGroupId(),dep.getArtifactId(),dep.getVersion());
    }

    public ModuleDependency(MavenProject project) {
        this(project.getGroupId(),project.getArtifactId(),project.getVersion());
    }

    public ModuleDependency(Plugin p) {
        this(p.getGroupId(),p.getArtifactId(), Functions.defaulted(p.getVersion(),NONE));
    }

    public ModuleDependency(ReportPlugin p) {
        this(p.getGroupId(),p.getArtifactId(),p.getVersion());
    }

    public ModuleDependency(Extension ext) {
        this(ext.getGroupId(),ext.getArtifactId(),ext.getVersion());
    }

    public ModuleName getName() {
        return new ModuleName(groupId,artifactId);
    }

    /**
     * Returns groupId+artifactId plus unknown version.
     */
    public ModuleDependency withUnknownVersion() {
        return new ModuleDependency(groupId,artifactId,UNKNOWN);
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ModuleDependency that = (ModuleDependency) o;

        return this.artifactId.equals(that.artifactId)
            && this.groupId.equals(that.groupId)
            && this.version.equals(that.version);
    }

    public int hashCode() {
        int result;
        result = groupId.hashCode();
        result = 31 * result + artifactId.hashCode();
        result = 31 * result + version.hashCode();
        return result;
    }

    /**
     * For compatibility reason, this value may be used in the verion field
     * to indicate that the version is unknown.
     */
    public static final String UNKNOWN = "*";

    /**
     * When a plugin dependency is specified without giving a version,
     * the semantics of that is the latest released plugin.
     * In this case, we don't want the {@link ModuleDependency} version to become
     * {@link #UNKNOWN}, which would match any builds of the plugin.
     *
     * <p>
     * So we use this constant to indicate a version, and this will not match
     * anything.
     *
     * @see #ModuleDependency(Plugin)
     */
    public static final String NONE = "-";

    private static final long serialVersionUID = 1L;
}
