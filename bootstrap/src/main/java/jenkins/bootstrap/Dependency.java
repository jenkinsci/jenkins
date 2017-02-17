package jenkins.bootstrap;

import hudson.util.VersionNumber;

/**
 * One dependency entry in {@link DependenciesTxt}.
 *
 * @author Kohsuke Kawaguchi*/
final class Dependency {
    /**
     * groupId, artifactId, and version.
     */
    final String g,a,v;
    final VersionNumber vv;
    final String ga;

    Dependency(String g, String a, String v) {
        this.g = g;
        this.a = a;
        this.v = v;
        this.vv = new VersionNumber(v);
        this.ga = g+':'+a;
    }
}
