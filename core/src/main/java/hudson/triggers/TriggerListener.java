package hudson.triggers;

import hudson.model.Job;
import hudson.model.Queue.Task;
import hudson.model.ParameterizedProjectTask;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.RunListener;

import java.util.HashMap;

/**
 * Use ItemListener and RunListener interfaces to record how each build
 * was triggered in the job console output.
 * @author Alan Harder
 */
public class TriggerListener extends ItemListener {

    private HashMap<Job,String> triggerMap = new HashMap<Job,String>();

    /**
     * Listen for scheduled builds and remember how they were triggered,
     * so that info can be recorded when build actually starts.
     */
    @Override
    public void onScheduled(Task task) {
        if (task instanceof ParameterizedProjectTask) {
            Job job = ((ParameterizedProjectTask)task).getProject();
            synchronized (this) {
                if (!triggerMap.containsKey(job))
                    triggerMap.put(job, ((ParameterizedProjectTask)task).getTriggeredBy());
            }
        }
    }

    @Override
    public void register() {
        super.register();
        new RunListener<Run>(Run.class) {
            @Override
            public void onStarted(Run r, TaskListener listener) {
                String triggeredBy;
                synchronized (TriggerListener.this) {
                    triggeredBy = triggerMap.remove(r.getParent());
                }
                if (triggeredBy != null) {
                    listener.getLogger().println("Build triggered by " + triggeredBy);
                }
            }
        }.register();
    }
}
