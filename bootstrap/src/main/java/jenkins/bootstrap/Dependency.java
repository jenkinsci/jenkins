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

    /**
     * classifier, if any.
     */
    final String c;

    Dependency(String g, String a, String v, String c) {
        this.g = g;
        this.a = a;
        this.v = v;
        this.c = "".equals(c) ? null : c;
        this.vv = new VersionNumber(v);
        this.ga = g+':'+a;
    }

    String getFileName() {
        return a+"-"+v+(c==null?"":"-"+c)+".jar";
    }

}
