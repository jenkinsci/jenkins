package hudson.util;

import jenkins.util.groovy.GroovyHookScript;
import org.kohsuke.stapler.WebApp;

import javax.servlet.ServletContext;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Indicates a fatal boot problem, among {@link ErrorObject}
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class BootFailure extends ErrorObject {
    protected BootFailure() {
    }

    protected BootFailure(Throwable cause) {
        super(cause);
    }

    /**
     * Exposes this failure to UI and invoke the hook.
     */
    public void publish(ServletContext context) {
        LOGGER.log(Level.SEVERE, "Failed to initialize Jenkins",this);

        WebApp.get(context).setApp(this);
        new GroovyHookScript("boot-failure")
                .bind("exception",this)
                .run();
    }

    private static final Logger LOGGER = Logger.getLogger(BootFailure.class.getName());
}
