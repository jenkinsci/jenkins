package jenkins.slaves;

import jenkins.security.HMACConfidentialKey;

/**
 * This class was part of the old JNLP1 protocol, which has been removed.
 * The SLAVE_SECRET was still used by some plugins. It has been moved to
 * JnlpAgentReceiver as a more suitable location, but this alias retained
 * for compatibility. References should be updated to the new location.
 */
@Deprecated
public class JnlpSlaveAgentProtocol {

    public static final HMACConfidentialKey SLAVE_SECRET = JnlpAgentReceiver.SLAVE_SECRET;

}
