package hudson;

import jenkins.model.Jenkins;
import jenkins.model.Jenkins.MasterComputer;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registers a DNS multi-cast service-discovery support.
 *
 * @author Kohsuke Kawaguchi
 */
public class DNSMultiCast implements Closeable {
    private JmDNS jmdns;

    public DNSMultiCast(final Jenkins jenkins) {
        if (disabled)   return; // escape hatch
        
        // the registerService call can be slow. run these asynchronously
        MasterComputer.threadPoolForRemoting.submit(new Callable<Object>() {
            public Object call() {
                try {
                    jmdns = JmDNS.create();

                    Map<String,String> props = new HashMap<String, String>();
                    String rootURL = jenkins.getRootUrl();
                    if (rootURL==null)  return null;

                    props.put("url", rootURL);
                    try {
                        props.put("version",String.valueOf(Jenkins.getVersion()));
                    } catch (IllegalArgumentException e) {
                        // failed to parse the version number
                    }

                    TcpSlaveAgentListener tal = jenkins.getTcpSlaveAgentListener();
                    if (tal!=null)
                        props.put("slave-port",String.valueOf(tal.getPort()));

                    props.put("server-id", jenkins.getLegacyInstanceId());

                    URL jenkins_url = new URL(rootURL);
                    int jenkins_port = jenkins_url.getPort();
                    if (jenkins_port == -1) {
                        jenkins_port = 80;
                    }
                    if (jenkins_url.getPath().length() > 0) {
                        props.put("path", jenkins_url.getPath());
                    }

                    jmdns.registerService(ServiceInfo.create("_hudson._tcp.local.","jenkins",
                            jenkins_port,0,0,props));	// for backward compatibility
                    jmdns.registerService(ServiceInfo.create("_jenkins._tcp.local.","jenkins",
                            jenkins_port,0,0,props));

                    // Make Jenkins appear in Safari's Bonjour bookmarks
                    jmdns.registerService(ServiceInfo.create("_http._tcp.local.","Jenkins",
                            jenkins_port,0,0,props));
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING,"Failed to advertise the service to DNS multi-cast",e);
                }
                return null;
            }
        });
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
