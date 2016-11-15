package jenkins;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Kill switch to disable the entire Jenkins CLI system.
 *
 * Marked as no external use because the CLI subsystem is nearing EOL.
 *
 * @author Kohsuke Kawaguchi
 */
@Restricted(NoExternalUse.class)
public class CLI {
    // non-final to allow setting from $JENKINS_HOME/init.groovy.d
    public static boolean DISABLED = Boolean.getBoolean(CLI.class.getName()+".disabled");
}
