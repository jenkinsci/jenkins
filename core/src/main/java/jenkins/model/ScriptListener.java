package jenkins.model;

import hudson.ExtensionPoint;
import jenkins.util.Listeners;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;

/**
 * A listener to track usage of the script console.
 *
 * @see Jenkins#_doScript(StaplerRequest, org.kohsuke.stapler.StaplerResponse, javax.servlet.RequestDispatcher, hudson.remoting.VirtualChannel, hudson.security.ACL)
 */
public interface ScriptListener extends ExtensionPoint {

    /**
     * Called when script is executed in Script console.
     *
     * @see Jenkins#_doScript(StaplerRequest, org.kohsuke.stapler.StaplerResponse, javax.servlet.RequestDispatcher, hudson.remoting.VirtualChannel, hudson.security.ACL)
     * @param req The StaplerRequest (POST) that contains the script to be executed.
     */
    void onScript(StaplerRequest req);

    /**
     * Fires the {@link #onScript(StaplerRequest)} event to track the usage of the script console.
     *
     * @see Jenkins#_doScript(StaplerRequest, org.kohsuke.stapler.StaplerResponse, javax.servlet.RequestDispatcher, hudson.remoting.VirtualChannel, hudson.security.ACL)
     * @param req The StaplerRequest (POST) that contians the script to be executed.
     */
    @Restricted(NoExternalUse.class)
    static void fireScriptEvent(StaplerRequest req) {
        Listeners.notify(ScriptListener.class, true, listener -> listener.onScript(req));
    }
}
