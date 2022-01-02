package jenkins.security.s2m;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import hudson.Extension;
import java.util.logging.Logger;

/**
 * @deprecated This class no longer has any effect.
 * Support for allowlisting {@link hudson.remoting.Callable}s predating the introduction of the {@link org.jenkinsci.remoting.RoleSensitive} interface for SECURITY-144 in 2014 has been dropped.
 * {@link hudson.FilePath} no longer supports being invoked from agents at all, so {@code FilePathFilter} etc. have been removed.
 *
 * @see <a href="https://www.jenkins.io/redirect/security-144/">Agent-to-controller security subsystem documentation</a>
 *
 */
@Deprecated
@Extension
public class AdminWhitelistRule {

    public AdminWhitelistRule() {
    }

    public boolean getMasterKillSwitch() {
        LOGGER.log(WARNING, "AdminWhitelistRule no longer has any effect but an attempt was made to read its current configuration value. See https://www.jenkins.io/redirect/AdminWhitelistRule to learn more.", new Exception());
        return false;
    }

    public void setMasterKillSwitch(boolean state) {
        if (state) {
            // an attempt to disable protections should warn
            LOGGER.log(WARNING, "Setting AdminWhitelistRule no longer has any effect. See https://www.jenkins.io/redirect/AdminWhitelistRule to learn more.", new Exception());
        } else {
            // This is basically no-op
            LOGGER.log(INFO, "Setting AdminWhitelistRule no longer has any effect. See https://www.jenkins.io/redirect/AdminWhitelistRule to learn more.", new Exception());
        }
    }

    private static final Logger LOGGER = Logger.getLogger(AdminWhitelistRule.class.getName());
}
