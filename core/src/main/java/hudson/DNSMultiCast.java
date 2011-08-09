package hudson;

import jenkins.model.Jenkins;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registers a DNS multi-cast service-discovery support.
 *
 * @author Kohsuke Kawaguchi
 */
public class DNSMultiCast implements Closeable {
    private JmDNS jmdns;

    public DNSMultiCast(Jenkins hudson) {
        if (disabled)   return; // escape hatch
        
        try {
            this.jmdns = JmDNS.create();

            Map<String,String> props = new HashMap<String, String>();
            String rootURL = hudson.getRootUrl();
            if (rootURL!=null)
                props.put("url", rootURL);
            try {
                props.put("version",String.valueOf(Jenkins.getVersion()));
            } catch (IllegalArgumentException e) {
                // failed to parse the version number
            }

            TcpSlaveAgentListener tal = hudson.getTcpSlaveAgentListener();
            if (tal!=null)
                props.put("slave-port",String.valueOf(tal.getPort()));

            props.put("server-id", Util.getDigestOf(hudson.getSecretKey()));

            jmdns.registerService(ServiceInfo.create("_hudson._tcp.local.","hudson",
                    80,0,0,props));	// for backward compatibility
            jmdns.registerService(ServiceInfo.create("_jenkins._tcp.local.","jenkins",
                    80,0,0,props));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,"Failed to advertise the service to DNS multi-cast",e);
        }
    }

    public void close() {
        if (jmdns!=null) {
//            try {
                jmdns.abort();
                jmdns = null;
//            } catch (final IOException e) {
//                LOGGER.log(Level.WARNING,"Failed to close down JmDNS instance!",e);
//            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(DNSMultiCast.class.getName());

    public static boolean disabled = Boolean.getBoolean(DNSMultiCast.class.getName()+".disabled");
}
