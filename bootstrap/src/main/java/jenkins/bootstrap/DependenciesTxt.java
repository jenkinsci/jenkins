package jenkins.bootstrap;

import hudson.util.VersionNumber;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses {@code dependencies.txt} produced by Maven dependency:list-dependencies mojo.
 *
 * @author Kohsuke Kawaguchi
 */
class DependenciesTxt {
    final List<Dependency> dependencies;
    /**
     * 'groupId:artifactId' to version number
     */
    private final Map<String,VersionNumber> jars = new HashMap<>();

    public DependenciesTxt(InputStream i) throws IOException {
        List<Dependency> list = new ArrayList<>();

        try (Reader r = new InputStreamReader(i,"UTF-8");
             BufferedReader br = new BufferedReader(r)) {

            // line that we care about has 5 components: groupId:artifactId:packaging:version:scope
            String line;
            while ((line=br.readLine())!=null) {
                String[] tokens = line.trim().split(":");
                if (tokens.length!=5)   continue;

                Dependency d = new Dependency(tokens[0], tokens[1], tokens[3]);
                list.add(d);
                jars.put(d.ga,d.vv);
            }
        }

        dependencies = Collections.unmodifiableList(list);
    }

    /**
     * Attempts to reverse map a jar file name back to {@link Dependency}
     */
    public Dependency fromFileName(String jarFileName) {
        for (Dependency d : dependencies) {
            if (jarFileName.equals(d.a+"-"+d.v+".jar"))
                return d;
        }
        return null;
    }

    /**
     * Does the core have a jar file newer than the specified version?
     */
    public boolean hasNewerThan(Dependency d) {
        VersionNumber x = jars.get(d.ga);
        return x != null && x.isNewerThan(d.vv);
    }

    public boolean contains(String ga) {
        return jars.containsKey(ga);
    }
}

