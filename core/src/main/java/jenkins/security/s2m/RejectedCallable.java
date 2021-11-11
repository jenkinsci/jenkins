package jenkins.security.s2m;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.PluginWrapper;
import jenkins.model.Jenkins;

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
        return Jenkins.get().pluginManager.whichPlugin(clazz);
    }
}
