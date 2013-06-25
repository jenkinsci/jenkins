package hudson.maven;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import jenkins.model.Jenkins;
import org.apache.maven.AbstractMavenLifecycleParticipant;

import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Contributes additional code into Plexus container when we run Maven.
 *
 * <p>
 * Injecting custom plexus components, such as {@link AbstractMavenLifecycleParticipant}, allows plugins to
 * participate into the Maven internals more deeply.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.519
 */
public abstract class PlexusModuleContributor implements ExtensionPoint, Serializable {
    public abstract List<URL> getPlexusComponentJars();

    /**
     * Returns all the registered {@link PlexusModuleContributor}s.
     */
    public static ExtensionList<PlexusModuleContributor> all() {
        return Jenkins.getInstance().getExtensionList(PlexusModuleContributor.class);
    }

    private static final long serialVersionUID = 1L;

    /**
     * Returns a single {@link PlexusModuleContributor} that aggregates all the registered
     * {@link PlexusModuleContributor}s in the system. The instance is remoting portable.
     */
    public static PlexusModuleContributor aggregate() {
        // capture in a serializable form
        final List<PlexusModuleContributor> all = new ArrayList<PlexusModuleContributor>(all());
        return new PlexusModuleContributor() {
            @Override
            public List<URL> getPlexusComponentJars() {
                List<URL> urls = new ArrayList<URL>();
                for (PlexusModuleContributor pc : all) {
                    urls.addAll(pc.getPlexusComponentJars());
                }
                return urls;
            }
        };
    }
}
