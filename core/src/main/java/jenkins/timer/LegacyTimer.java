package jenkins.timer;

import hudson.Extension;
import hudson.triggers.Trigger;

import java.util.TimerTask;

/**
 * A timer which uses the legacy {@link java.util.Timer} {@link Trigger#timer}.
 *
 * This implementation suffers from <a href="https://issues.jenkins-ci.org/browse/JENKINS-19622">
 *     JENKINS-19622</a>. IE, it can be blocked by poorly written plugins.
 *
 *  @deprecated Use {@link ScheduledExecutorTimer} instead.
 */
@Extension
@Deprecated
public class LegacyTimer extends Timer {
    @Override
    public void scheduleWithFixedDelay(TimerTask task, long delay, long period) {
        java.util.Timer timer = Trigger.timer;
        if (timer != null) {
            timer.schedule(task, delay, period);
        }
    }

    @Override
    public void schedule(TimerTask task, long delay) {
        java.util.Timer timer = Trigger.timer;
        if (timer != null) {
            timer.schedule(task, delay);
        }
    }

    @Override
    public void scheduleAtFixedRate(TimerTask task, long firstTime, long period) {
        java.util.Timer timer = Trigger.timer;
        if (timer != null) {
            timer.scheduleAtFixedRate(task, firstTime, period);
        }
    }

    @Override
    public void shutdown() {
        java.util.Timer timer = Trigger.timer;
        if (timer != null) {
            timer.cancel();
        }
        Trigger.timer = null;
    }
}
