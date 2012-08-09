/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Olivier Lamy
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.maven;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.project.MavenProject;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.Extension;

import java.io.Serializable;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import hudson.Functions;

/**
 * group id + artifact id + version and a flag to know if it's a plugin 
 *
 * @author Kohsuke Kawaguchi
 * @see ModuleName
 */
public final class ModuleDependency implements Serializable {
    public final String groupId;
    public final String artifactId;
    public final String version;
    
    /**
     * @since 1.395
     */
    public final boolean plugin;

    public ModuleDependency(String groupId, String artifactId, String version) {
        this(groupId, artifactId, version, false);
    }
    
    public ModuleDependency(String groupId, String artifactId, String version, boolean plugin) {
        this.groupId = groupId.intern();
        this.artifactId = artifactId.intern();
        if(version==null)
            this.version = UNKNOWN;
        else
            this.version = version.intern();
        this.plugin = plugin;
    }

    public ModuleDependency(ModuleName name, String version) {
        this(name.groupId,name.artifactId,version,false);
    }
    
    public ModuleDependency(ModuleName name, String version, boolean plugin) {
        this(name.groupId,name.artifactId,version,plugin);
    }

    public ModuleDependency(org.apache.maven.model.Dependency dep) {
        this(dep.getGroupId(),dep.getArtifactId(),dep.getVersion());
    }

    public ModuleDependency(MavenProject project) {
        this(project.getGroupId(),project.getArtifactId(),project.getVersion());
    }

    public ModuleDependency(Plugin p) {
        this(p.getGroupId(),p.getArtifactId(), Functions.defaulted(p.getVersion(),NONE),true);
    }

    public ModuleDependency(ReportPlugin p) {
        this(p.getGroupId(),p.getArtifactId(),p.getVersion(),true);
    }

    public ModuleDependency(Extension ext) {
        this(ext.getGroupId(),ext.getArtifactId(),ext.getVersion());
    }

    private ModuleDependency(String groupId, String artifactId, boolean plugin) {
        // to be used only by the withUnknownVersion() method
        // where we know that groupId and artifactId are already interned
        // and where we want an UNKNOWN version
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = UNKNOWN;
        this.plugin = plugin;
    }
    
    public ModuleName getName() {
        return new ModuleName(groupId,artifactId);
    }

    /**
     * Returns groupId+artifactId plus unknown version.
     */
    public ModuleDependency withUnknownVersion() {
        if (UNKNOWN.equals(version))
            return this;
        else
            return new ModuleDependency(groupId,artifactId,plugin);
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ModuleDependency that = (ModuleDependency) o;

        return this.artifactId.equals(that.artifactId)
            && this.groupId.equals(that.groupId)
            && this.version.equals(that.version)
            && this.plugin == that.plugin;
    }

    public int hashCode() {
        int result;
        result = groupId.hashCode();
        result = 31 * result + artifactId.hashCode();
        result = 31 * result + version.hashCode();
        result = 31 * result + (plugin ? 1 : 2);
        return result;
    }

    /**
     * Upon reading from the disk, intern strings.
     */
    protected Object readResolve() {
        return new ModuleDependency(groupId,artifactId,version,plugin);
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
    /**
     * Checks whether this ModuleDependency is satisfied by the dependency of the given ModuleDependency.
     * This caters for versions where the version string defines a version range. It is assumed that the given
     * ModuleDependency is of the same groupId and artifactId and only validates against the version.
     *
     * @param otherDependency The dependency to check for.
     * @return true if contained false otherwise.
     */
    public boolean isSatisfiedBy(ModuleDependency otherDependency) {
        if (otherDependency == null) {
            return false;
        }

        boolean result = false;
        try {
            VersionRange myRange = VersionRange.createFromVersionSpec(version);
            ArtifactVersion otherVersion = new DefaultArtifactVersion(otherDependency.version);
            result = myRange.containsVersion(otherVersion);
        } catch (InvalidVersionSpecificationException ivse) {
            //Do nothing. Return false.
        }

        return result;
    }

    /**
     * Given a list of ModuleDependencies of the same groupId and artifactId, it will sort this list using the
     * version and finds the highest version that satisfies this ModuleDependency.
     *
     * @param listModuleDependencies The list of ModuleDependencies.
     * @return The highest satisfying ModuleDependency or null if none can be found.
     */
    public ModuleDependency findHighestSatisfyingModule(List<ModuleDependency> listModuleDependencies) {

        ModuleDependency highestDependency = null;

        //Create a sorted map of the ModuleDependnecies sorted on version.
        SortedMap<ArtifactVersion, ModuleDependency> sorted = new TreeMap<ArtifactVersion, ModuleDependency>();
        for (ModuleDependency otherDependency : listModuleDependencies) {
            sorted.put(new DefaultArtifactVersion(otherDependency.version), otherDependency);
        }

        try {
            VersionRange myRange = VersionRange.createFromVersionSpec(version);

            //Now find the highest version that satisfies this dependency.
            ArtifactVersion highestVersion = null;
            for (ArtifactVersion version : sorted.keySet()) {
                if (myRange.containsVersion(version)) {
                    highestVersion = version;
                } else {
                    break;
                }
            }

            if (highestVersion != null) {
                highestDependency = sorted.get(highestVersion);
            }
        } catch (InvalidVersionSpecificationException e) {
            //Do nothing - return null.
        }
        return highestDependency;
    }

    @Override
    public String toString() {
        return "ModuleDependency{" +
               "groupId='" + groupId + '\'' +
               ", artifactId='" + artifactId + '\'' +
               ", version='" + version + '\'' +
               ", plugin=" + plugin +
               '}';
    }
}
