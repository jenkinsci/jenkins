package hudson;

import jenkins.util.SystemProperties;
import jenkins.model.Jenkins;
import jenkins.model.Jenkins.MasterComputer;

import javax.jmdns.ServiceInfo;
import javax.jmdns.impl.JmDNSImpl;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Registers a DNS multi-cast service-discovery support.
 *
 * @author Kohsuke Kawaguchi
 */
public class DNSMultiCast implements Closeable {
    private JenkinsJmDNS jmdns;

    public DNSMultiCast(final Jenkins jenkins) {
        if (disabled)   return; // escape hatch
        
        // the registerService call can be slow. run these asynchronously
        MasterComputer.threadPoolForRemoting.submit(new Callable<Object>() {
            public Object call() {
                try {
                    jmdns = new JenkinsJmDNS(null, null);

                    Map<String,String> props = new HashMap<>();
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
            try {
                jmdns.abort();
                jmdns = null;
            } catch (final IOException e) {
                LOGGER.log(Level.WARNING,"Failed to close down JmDNS instance!",e);
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(DNSMultiCast.class.getName());

    public static boolean disabled = SystemProperties.getBoolean(DNSMultiCast.class.getName()+".disabled");

    /**
     * Class that extends {@link JmDNSImpl} to add an abort method. Since {@link javax.jmdns.JmDNS#close()} might
     * make the instance hang during the shutdown, the abort method terminate uncleanly, but rapidly and
     * without blocking.
     *
     * Initially it was part of the jenkinsci/jmdns forked library, but now this class is responsible for aborting,
     * allowing to have a direct and clean dependency to the original library.
     *
     * The abort() method is pretty similar to close() method. To access private methods and fields uses
     * reflection.
     *
     * @since 2.178
     *
     * See JENKINS-25369 for further details
     */
    private static class JenkinsJmDNS extends JmDNSImpl {
        private static Logger logger = Logger.getLogger(JmDNSImpl.class.getName());
        private final Class parent;

        /**
         * Create an instance of JmDNS and bind it to a specific network interface given its IP-address.
         *
         * @param address IP address to bind to.
         * @param name    name of the newly created JmDNS
         * @throws IOException
         */
        public JenkinsJmDNS(InetAddress address, String name) throws IOException {
            super(address, name);
            this.parent = this.getClass().getSuperclass();
        }

        /**
         * Works like {@link #close()} but terminate uncleanly, but rapidly and without blocking.
         */
        public void abort() throws IOException {
            if (this.isClosing()) {
                return;
            }

           if (logger.isLoggable(Level.FINER)) {
                logger.finer("Aborting JmDNS: " + this);
            }
            // Stop JmDNS
            // This protects against recursive calls
            if (this.closeState()) {
                // We got the tie break now clean up

                // Stop the timer
                logger.finer("Canceling the timer");
                this.cancelTimer();

                // Cancel all services
                // KK: this is a blocking call that doesn't fit 'abort'
                // this.unregisterAllServices();
                executePrivateParentMethod("disposeServiceCollectors");

// KK: another blocking call
//                if (logger.isLoggable(Level.FINER)) {
//                    logger.finer("Wait for JmDNS cancel: " + this);
//                }
//                this.waitForCanceled(DNSConstants.CLOSE_TIMEOUT);

                // Stop the canceler timer
                logger.finer("Canceling the state timer");
                this.cancelStateTimer();

                // Stop the executor
                shutdown();

                // close socket
                executePrivateParentMethod("closeMulticastSocket");

                // remove the shutdown hook
                if (_shutdown != null) {
                    Runtime.getRuntime().removeShutdownHook(_shutdown);
                }

                if (logger.isLoggable(Level.FINER)) {
                    logger.finer("JmDNS closed.");
                }
            }
            advanceState(null);
        }

        private void shutdown() throws IOException {
            try {
                Field executor = this.parent.getDeclaredField("_executor");
                executor.setAccessible(true);
                ExecutorService _executor = (ExecutorService) executor.get(this);
                _executor.shutdown();
            } catch (NoSuchFieldException | IllegalAccessException e) {
                logger.log(Level.SEVERE, "Error trying to abort JmDNS", e);
                throw new IOException(e);
            }
        }

        private void executePrivateParentMethod(String method) throws IOException {
            try {
                Method m = this.parent.getDeclaredMethod(method);
                m.setAccessible(true);
                m.invoke(this);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                logger.log(Level.SEVERE, "Error trying to abort JmDNS", e);
                throw new IOException(e);
            }
        }

    }
}
