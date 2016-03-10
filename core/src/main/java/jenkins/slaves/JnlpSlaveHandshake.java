package jenkins.slaves;

import org.jenkinsci.remoting.engine.JnlpServerHandshake;
import org.jenkinsci.remoting.nio.NioChannelHub;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

/**
 * Palette of objects to talk to the incoming JNLP slave connection.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.561
 * @deprecated as of 1.609
 *      Use {@link JnlpServerHandshake}
 */
public class JnlpSlaveHandshake extends JnlpServerHandshake {
    /*package*/ JnlpSlaveHandshake(JnlpServerHandshake rhs) {
        super(rhs);
    }
}
