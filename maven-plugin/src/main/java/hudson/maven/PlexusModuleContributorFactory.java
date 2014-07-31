package hudson.maven;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.AbstractBuild;
import jenkins.model.Jenkins;
import org.apache.maven.AbstractMavenLifecycleParticipant;

import java.io.IOException;
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
 * <h2>Lifecycle</h2>
 * <p>
 * {@link PlexusModuleContributorFactory}s are instantiated as singletons on the master, and whenever a new Maven
 * process starts, its {@link #createFor(AbstractBuild)} method is called to instantiate {@link PlexusModuleContributor},
 * which gets  serialized on the master, then deserialized inside the Maven process, and then its
 * {@link PlexusModuleContributor#getPlexusComponentJars()} will be invoked to determine the additional classpaths.
 * and then run.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.521
 * @see PlexusModuleContributor
 */
public abstract class PlexusModuleContributorFactory implements ExtensionPoint {

    public abstract PlexusModuleContributor createFor(AbstractBuild<?,?> context) throws IOException, InterruptedException;

    /**
     * Returns all the registered {@link PlexusModuleContributor}s.
     */
    public static ExtensionList<PlexusModuleContributorFactory> all() {
        return Jenkins.getInstance().getExtensionList(PlexusModuleContributorFactory.class);
    }

    /**
     * Returns a single {@link PlexusModuleContributor} that aggregates all the registered
     * {@link PlexusModuleContributor}s in the system. The instance is remoting portable.
     */
    public static PlexusModuleContributor aggregate(AbstractBuild<?,?> context) throws IOException, InterruptedException {
        // capture in a serializable form
        final List<PlexusModuleContributor> all = new ArrayList<PlexusModuleContributor>();
        for (PlexusModuleContributorFactory pmcf : all()) {
            PlexusModuleContributor pmc = pmcf.createFor(context);
            if (pmc!=null)
                all.add(pmc);
        }

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
