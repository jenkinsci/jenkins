package hudson.maven;

import org.apache.maven.embedder.MavenEmbedder;
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

        maven.setClassLoader(Thread.currentThread().getContextClassLoader());
        maven.setLogger( new EmbedderLoggerImpl(listener) );

        maven.start();

        return maven;
    }
}
