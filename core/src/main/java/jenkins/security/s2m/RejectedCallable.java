package jenkins.security.s2m;

import hudson.PluginWrapper;
import jenkins.model.Jenkins;

import javax.annotation.CheckForNull;

/**
* @author Kohsuke Kawaguchi
*/
public /*for Jelly*/ class RejectedCallable {
    public final Class clazz;

    /*package*/ RejectedCallable(Class clazz) {
        this.clazz = clazz;
    }

    public @CheckForNull
    PluginWrapper getPlugin() {
        return Jenkins.getInstance().pluginManager.whichPlugin(clazz);
    }
}
