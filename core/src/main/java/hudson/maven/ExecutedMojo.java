package hudson.maven;

import hudson.Util;
import hudson.remoting.Which;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;

/**
 * Persisted record of mojo execution.
 *
 * <p>
 * This information is first recorded in the maven process, then sent over
 * the remoting layer to the master, then persisted via XStream.
 *
 * @author Kohsuke Kawaguchi
 */
public final class ExecutedMojo implements Serializable {
    /**
     * Plugin group ID.
     */
    public final String groupId;
    /**
     * Plugin artifact ID.
     */
    public final String artifactId;
    /**
     * Plugin version.
     */
    public final String version;
    /**
     * Mojo name.
     */
    public final String goal;
    /**
     * Optional execution ID, when the same mojo gets executed multiple times.
     */
    public final String executionId;
    /**
     * How long did it take to execute this goal? in milli-seconds.
     * (precision might not be 1ms)
     */
    public final long duration;
    /**
     * MD5 hash of the plugin jar.
     */
    public final String digest;

    ExecutedMojo(MojoInfo mojo, long duration) throws IOException, InterruptedException {
        this.groupId = mojo.pluginName.groupId;
        this.artifactId = mojo.pluginName.artifactId;
        this.version = mojo.pluginName.version;
        this.goal = mojo.getGoal();
        this.executionId = mojo.mojoExecution.getExecutionId();
        this.duration = duration;

        String digest = null;
        try {
            MojoDescriptor md = mojo.mojoExecution.getMojoDescriptor();
            PluginDescriptor pd = md.getPluginDescriptor();
            Class clazz = pd.getClassRealm().loadClass(md.getImplementation());
            digest = Util.getDigestOf(new FileInputStream(Which.jarFile(clazz)));
        } catch (ClassNotFoundException e) {
            // perhaps the plugin has failed to load.
        }
        this.digest = digest;
    }
}
