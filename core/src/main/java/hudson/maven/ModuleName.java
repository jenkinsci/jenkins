package hudson.maven;

import org.apache.maven.project.MavenProject;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.ReportPlugin;

import java.io.Serializable;

/**
 * Version independent name of a Maven project.
 * 
 * @author Kohsuke Kawaguchi
 * @see ModuleDependency
 */
public class ModuleName implements Comparable<ModuleName>, Serializable {
    public final String groupId;
    public final String artifactId;

    public ModuleName(String groupId, String artifactId) {
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    public ModuleName(MavenProject project) {
        this(project.getGroupId(),project.getArtifactId());
    }

    public ModuleName(Plugin plugin) {
        this(plugin.getGroupId(),plugin.getArtifactId());
    }

    public ModuleName(ReportPlugin plugin) {
        this(plugin.getGroupId(),plugin.getArtifactId());
    }

    public ModuleName(Extension ext) {
        this(ext.getGroupId(),ext.getArtifactId());
    }

    public ModuleName(Dependency dep) {
        this(dep.getGroupId(),dep.getArtifactId());
    }

    /**
     * Returns the "groupId:artifactId" form.
     */
    public String toString() {
        return groupId+':'+artifactId;
    }

    /**
     * Returns the "groupId$artifactId" form,
     * which is safe for the use as a file name, unlike {@link #toString()}.
     */
    public String toFileSystemName() {
        return groupId+'$'+artifactId;
    }

    public static ModuleName fromFileSystemName(String n) {
        int idx = n.indexOf('$');
        if(idx<0)   throw new IllegalArgumentException(n);
        return new ModuleName(n.substring(0,idx),n.substring(idx+1));
    }

    public static ModuleName fromString(String n) {
        int idx = Math.max(n.indexOf(':'),n.indexOf('$'));
        if(idx<0)   throw new IllegalArgumentException(n);
        return new ModuleName(n.substring(0,idx),n.substring(idx+1));
    }

    /**
     * Checks if the given name is valid module name string format
     * created by {@link #toString()}.
     */
    public static boolean isValid(String n) {
        return Math.max(n.indexOf(':'),n.indexOf('$'))>0;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ModuleName that = (ModuleName) o;

        return artifactId.equals(that.artifactId)
            && groupId.equals(that.groupId);

    }

    public int hashCode() {
        int result;
        result = groupId.hashCode();
        result = 31 * result + artifactId.hashCode();
        return result;
    }

    public int compareTo(ModuleName that) {
        int r = this.groupId.compareTo(that.groupId);
        if(r!=0)    return r;
        return this.artifactId.compareTo(that.artifactId);
    }

    private static final long serialVersionUID = 1L;
}
