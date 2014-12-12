package jenkins.security;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.remoting.Channel;
import hudson.remoting.ChannelBuilder;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;

import javax.annotation.Nullable;

/**
 * Intercepts the new creation of {@link Channel} and tweak its configuration.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.THU
 */
public abstract class ChannelConfigurator implements ExtensionPoint {
    /**
     * Called whenever a new channel is being built.
     *
     * @param builder
     *      Configures the newly built channel. The callee
     *      can call its methods to modify its settings.
     * @param context
     *      The parameter that helps the callee determines what this channel is for.
     *      Legacy callers do not always provide this information, in which case this value might be null.
     *
     *      Possible known values include:
     *
     *      <dl>
     *          <dt>{@link SlaveComputer}
     *          <dd>When a channel is being established to talk to a slave.
     *      </dl>
     */
    public void onChannelBuilding(ChannelBuilder builder, @Nullable Object context) {}

    /**
     * All the registered {@link ChannelConfigurator}s.
     */
    public static ExtensionList<ChannelConfigurator> all() {
        return Jenkins.getInstance().getExtensionList(ChannelConfigurator.class);
    }
}
