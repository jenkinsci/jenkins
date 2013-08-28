package hudson.maven;

import hudson.FilePath;
import hudson.remoting.Channel;

import java.io.File;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;

/**
 * Contributes additional code into Plexus container when we run Maven.
 *
 * <p>
 * This object gets serialized and is sent to Maven JVM to run.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.521
 * @see PlexusModuleContributorFactory
 */
public abstract class PlexusModuleContributor implements Serializable {
    /**
     * Designates the list of URLs to be added to the classpath of the core plexus components
     * that constitute Maven.
     */
    public abstract List<URL> getPlexusComponentJars();

    /**
     * When {@link #getPlexusComponentJars()} is called, this field is set
     * to the channel that represents the connection to the master.
     */
    protected transient Channel channel;

    protected Object readResolve() {
        channel = Channel.current();
        return this;
    }

    private static final long serialVersionUID = 1L;

    public static PlexusModuleContributor of(FilePath... jars) {
        return of(asList(jars));
    }

    /**
     * Convenience method that creates a {@link PlexusModuleContributor} object
     * that adds the given files as classpaths.
     *
     * These jar files must represent the files on the computer on which Maven process is running.
     */
    public static PlexusModuleContributor of(List<FilePath> jars) {
        final List<String> files = new ArrayList<String>(jars.size());
        for (FilePath jar : jars) {
            files.add(jar.getRemote());
        }

        return new PlexusModuleContributor() {
            @Override
            public List<URL> getPlexusComponentJars() {
                try {
                    List<URL> r = new ArrayList<URL>(files.size());
                    for (String file : files) {
                        r.add(new File(file).toURI().toURL());
                    }
                    return r;
                } catch (MalformedURLException e) {
                    throw new IllegalStateException(e);
                }
            }

            private static final long serialVersionUID = 1L;
        };
    }
}
