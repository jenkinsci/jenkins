package hudson.triggers;

import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * {@link Timer} wrapper so that a fatal error in {@link TimerTask}
 * won't terminate the timer.
 *
 * <p>
 * {@link Trigger#timer} is a shared timer instance that can be used inside Hudson to
 * schedule a recurring work.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.124
 * @see Trigger#timer
 */
public abstract class SafeTimerTask extends TimerTask {
    public final void run() {
        try {
            doRun();
        } catch(Throwable t) {
            LOGGER.log(Level.SEVERE, "Timer task "+this+" failed",t);
        }
    }

    protected abstract void doRun();

    private static final Logger LOGGER = Logger.getLogger(SafeTimerTask.class.getName());
}
