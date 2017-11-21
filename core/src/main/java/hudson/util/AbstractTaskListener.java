package hudson.util;

import hudson.RestrictedSince;
import hudson.model.TaskListener;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;


/**
 * @deprecated implement {@link TaskListener} directly
 */
@Deprecated
@Restricted(NoExternalUse.class)
@RestrictedSince("2.91")
public abstract class AbstractTaskListener implements TaskListener {
}
