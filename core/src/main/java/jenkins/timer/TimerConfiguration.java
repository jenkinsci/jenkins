package jenkins.timer;

import hudson.Extension;
import hudson.util.VersionNumber;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;

/**
 * Allow users to configure their Timer.
 */
@Extension
public class TimerConfiguration extends GlobalConfiguration {

    private Timer timer = null;

    public TimerConfiguration() {
        load();
        if (timer == null) {
            if (Jenkins.getInstance().isUpgradedFromBefore(new VersionNumber("1.537"))) {
                timer = Timer.all().get(LegacyTimer.class);
            } else {
                timer = Timer.all().get(ScheduledExecutorTimer.class);
            }
        }
    }
    public static Timer getTimer() {
        return get().timer;
    }

    public static TimerConfiguration get() {
        return Jenkins.getInstance().getInjector().getInstance(TimerConfiguration.class);
    }

}
