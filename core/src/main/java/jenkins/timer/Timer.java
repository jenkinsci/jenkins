package jenkins.timer;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.init.Initializer;
import hudson.model.AperiodicWork;
import hudson.model.ComputerSet;
import hudson.model.PeriodicWork;
import hudson.triggers.SafeTimerTask;
import hudson.util.DoubleLaunchChecker;
import jenkins.model.Jenkins;

import java.util.TimerTask;

import static hudson.init.InitMilestone.JOB_LOADED;

/**
 * An abstraction of the low-level Timer service in Jenkins.
 *
 * NOTE: Plugins should prefer using {@link PeriodicWork},
 * {@link AperiodicWork}, {@link hudson.model.AsyncPeriodicWork}
 * or {@link hudson.model.AsyncAperiodicWork} as they provide higher level
 * abstractions.
 */
public abstract class Timer implements ExtensionPoint {

    public abstract void scheduleWithFixedDelay(TimerTask task, long delay, long period);

    public abstract void schedule(TimerTask task, long delay);

    public abstract void scheduleAtFixedRate(TimerTask task, long delay, long period);

    public abstract void shutdown();

    public static ExtensionList<Timer> all() {
        return Jenkins.getInstance().getExtensionList(Timer.class);
    }

    @Initializer(after=JOB_LOADED)
    public static void init() {
        new DoubleLaunchChecker().schedule();

        Timer _timer = TimerConfiguration.getTimer();
        // start all PeridocWorks
        for(PeriodicWork p : PeriodicWork.all()) {
            _timer.scheduleAtFixedRate(p,p.getInitialDelay(),p.getRecurrencePeriod());
        }

        // start all AperidocWorks
        for(AperiodicWork p : AperiodicWork.all()) {
            _timer.schedule(p,p.getInitialDelay());
        }

        // start monitoring nodes, although there's no hurry.
        _timer.schedule(new SafeTimerTask() {
            public void doRun() {
                ComputerSet.initialize();
            }
        }, 1000*10);

    }


}
