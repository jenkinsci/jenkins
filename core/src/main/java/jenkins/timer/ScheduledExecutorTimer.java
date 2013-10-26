package jenkins.timer;

import hudson.Extension;

import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A timer which uses a {@link ScheduledExecutorService} to schedule tasks.
 *
 * Additional threads will be created to schedule tasks as they become due.
 */
@Extension
public class ScheduledExecutorTimer extends Timer {

    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

    @Override
    public void scheduleWithFixedDelay(TimerTask task, long delay, long period) {
        executorService.scheduleWithFixedDelay(task, delay, period, TimeUnit.MILLISECONDS);
    }

    @Override
    public void schedule(TimerTask task, long delay) {
        executorService.schedule(task,delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void scheduleAtFixedRate(TimerTask task, long delay, long period) {
        executorService.scheduleAtFixedRate(task, delay, period, TimeUnit.MILLISECONDS);
    }

    @Override
    public void shutdown() {
        executorService.shutdownNow();
    }
}
