package hudson;

import hudson.model.Hudson;

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

    public DNSMultiCast(Hudson hudson) {
        try {
            this.jmdns = JmDNS.create();

            Map<String,String> props = new HashMap<String, String>();
            String rootURL = hudson.getRootUrl();
            if (rootURL!=null)
                props.put("url", rootURL);
            props.put("version",Hudson.getVersion().toString());

            TcpSlaveAgentListener tal = hudson.getTcpSlaveAgentListener();
            if (tal!=null)
                props.put("slave-port",String.valueOf(tal.getPort()));

            jmdns.registerService(ServiceInfo.create("_hudson._tcp.local.","hudson",
                    80,0,0,props));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,"Failed to advertise the service to DNS multi-cast",e);
        }
    }

    public void close() {
        jmdns.close();
    }

    private static final Logger LOGGER = Logger.getLogger(DNSMultiCast.class.getName());
}
