package hudson;

import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import jenkins.model.Jenkins;
import jenkins.model.Jenkins.MasterComputer;
import net.sf.json.JSONObject;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import org.kohsuke.stapler.StaplerRequest;

import com.google.inject.Injector;

import hudson.init.InitMilestone;
import hudson.init.Initializer;

import static java.util.logging.Level.SEVERE;

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
    public static boolean disabled = Boolean.getBoolean(DNSMultiCast.class.getName()+".disabled");

    private static final Logger LOGGER = Logger.getLogger(DNSMultiCast.class.getName());

    private static transient DNSMultiCast dnsMultiCast;

    /**
     * Starts the DNS Multicast services, if enabled
     */
    @Initializer(after=InitMilestone.PLUGINS_PREPARED)
    public static void startDnsMulticast() {
        if(disabled) { // this will force disabling DNS
            return;
        }
        if(dnsMultiCast == null) {
            // check config for this feature being enabled
            Jenkins jenkins = Jenkins.getInstance();
            Injector injector = jenkins.getInjector();
            GlobalDNSMultiCastConfiguration config = injector.getInstance(GlobalDNSMultiCastConfiguration.class);
            if(config.isDnsMultiCastEnabled()) {
                dnsMultiCast = new DNSMultiCast(jenkins);
            }
        }
    }
    
    /**
     * Stops the DNS Multicast services if running
     */
    @hudson.init.Terminator
    public static void stopDnsMulticast() throws Exception {
        if(dnsMultiCast!=null) {
            LOGGER.log(Level.FINE, "Closing DNS Multicast service");
            try {
                dnsMultiCast.close();
                dnsMultiCast = null;
            } catch (OutOfMemoryError e) {
                // we should just propagate this, no point trying to log
                throw e;
            } catch (LinkageError e) {
                LOGGER.log(SEVERE, "Failed to close DNS Multicast service", e);
                // safe to ignore and continue for this one
            } catch (Exception e) {
                LOGGER.log(SEVERE, "Failed to close DNS Multicast service", e);
                // save for later
                throw e;
            }
        }
    }

    private JmDNS jmdns;

    public DNSMultiCast(final Jenkins jenkins) {
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
                    LOGGER.log(Level.INFO, "Cannot advertise service to DNS multi-cast, skipping: {0}", e);
                    LOGGER.log(Level.FINE, null, e);
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

    @Extension(ordinal=196) // after GlobalCrumbIssuerConfiguration
    public static class GlobalDNSMultiCastConfiguration extends GlobalConfiguration {
        public GlobalDNSMultiCastConfiguration() {
            load();
        }
        
        // JENKINS-33596 - disable jmDNS by default
        private boolean isDnsMultiCastEnabled = false;
        
        @Override
        public GlobalConfigurationCategory getCategory() {
            return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
        }
        
        public boolean isDnsMultiCastEnabled() {
            return isDnsMultiCastEnabled;
        }
        
        public void setDnsMultiCastEnabled(boolean isDnsMultiCastEnabled) {
            this.isDnsMultiCastEnabled = isDnsMultiCastEnabled;
        }
        
        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            // for compatibility reasons, the actual value is stored in Jenkins
            if (json.getBoolean("dnsMultiCastEnabled")) {
                isDnsMultiCastEnabled = true;
                startDnsMulticast();
            } else {
                isDnsMultiCastEnabled = false;
                try {
                    stopDnsMulticast();
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Unable to stop DNS Multicast Services: ", e);
                }
            }
            save();
            return true;
        }
    }
}
