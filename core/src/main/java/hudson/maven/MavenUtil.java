package hudson.maven;

import org.apache.maven.embedder.MavenEmbedderException;
import hudson.model.TaskListener;

/**
 * @author Kohsuke Kawaguchi
 */
class MavenUtil {
    /**
     * Creates a fresh {@link MavenEmbedder} instance.
     *
     * @param listener
     *      This is where the log messages from Maven will be recorded.
     */
    public static MavenEmbedder createEmbedder(TaskListener listener) throws MavenEmbedderException {
        MavenEmbedder maven = new MavenEmbedder();

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        maven.setClassLoader(cl);
        maven.setLogger( new EmbedderLoggerImpl(listener) );
        // if we let Plexus find components, there's no guaranteed ordering,
        // so Plexus may well find the DefaultPluginManager from maven.jar instead of
        // our override. So use this mechanism to make sure ours are loaded first
        // before Plexus goes service loader discovery.
        maven.setOverridingComponentsXml(cl.getResource("META-INF/plexus/hudson-components.xml"));

        maven.start();

        return maven;
    }
}
