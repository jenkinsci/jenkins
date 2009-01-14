package hudson.lifecycle;

import java.io.IOException;

/**
 * {@link Lifecycle} for Hudson installed as SMF service.
 *
 * @author Kohsuke Kawaguchi
 */
public class SolarisSMFLifecycle extends Lifecycle {
    /**
     * In SMF managed environment, just commit a suicide and the service will be restarted by SMF.
     */
    @Override
    public void restart() throws IOException, InterruptedException {
        System.exit(0);
    }
}
