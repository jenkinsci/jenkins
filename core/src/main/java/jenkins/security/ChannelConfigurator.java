package jenkins.security;

import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Proc;
import hudson.remoting.Channel;
import hudson.remoting.ChannelBuilder;
import hudson.agents.Channels;
import hudson.agents.AgentComputer;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;

/**
 * Intercepts the new creation of {@link Channel} and tweak its configuration.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.587 / 1.580.1
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
     *          <dt>{@link AgentComputer}
     *          <dd>When a channel is being established to talk to a agent.
     *          <dt>{@link Proc}
     *          <dd>When {@link Channels#forProcess(String, ExecutorService, Process, OutputStream)} or overloads are used without a contextual {@link AgentComputer}.
     *      </dl>
     */
    public void onChannelBuilding(ChannelBuilder builder, @Nullable Object context) {}

    /**
     * All the registered {@link ChannelConfigurator}s.
     */
    public static ExtensionList<ChannelConfigurator> all() {
        return ExtensionList.lookup(ChannelConfigurator.class);
    }
}
