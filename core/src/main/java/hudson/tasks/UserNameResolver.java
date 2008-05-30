package hudson.tasks;

import hudson.ExtensionPoint;
import hudson.Plugin;
import hudson.scm.SCM;
import hudson.scm.CVSSCM;
import hudson.scm.SubversionSCM;
import hudson.model.User;
import hudson.model.AbstractProject;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Finds full name off the user when none is specified.
 *
 * <p>
 * This is an extension point of Hudson. Plugins tha contribute new implementation
 * of this class must register it to {@link #LIST}. The typical way to do this is:
 *
 * <pre>
 * class PluginImpl extends {@link Plugin} {
 *   public void start() {
 *     ...
 *     UserNameResolver.LIST.add(new UserNameResolver());
 *   }
 * }
 * </pre>
 *
 * @author Kohsuke Kawaguchi
 * @sine 1.192
 */
public abstract class UserNameResolver implements ExtensionPoint {

	/**
     * Finds full name of the given user.
     *
     * <p>
     * This method is called when a {@link User} without explicitly name is used.
     *
     * <p>
     * When multiple resolvers are installed, they are consulted in order and
     * the search will be over when a name is found by someoene.
     *
     * <p>
     * Since {@link UserNameResolver} is singleton, this method can be invoked concurrently
     * from multiple threads.
     *
     * @return
     *      null if the inference failed.
     */
    public abstract String findNameFor(User u);
    
    public static String resolve(User u) {
        for (UserNameResolver r : LIST) {
            String name = r.findNameFor(u);
            if(name!=null) return name;
        }

            return null;
    }

    /**
     * All registered {@link UserNameResolver} implementations.
     */
    public static final List<UserNameResolver> LIST = new CopyOnWriteArrayList<UserNameResolver>();

}
