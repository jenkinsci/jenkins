package hudson.model.queue;

import com.google.common.collect.Iterables;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Executor;
import jenkins.model.Jenkins;
import hudson.model.InvisibleAction;
import hudson.model.Queue.BuildableItem;
import hudson.model.queue.MappingWorksheet.ExecutorChunk;
import hudson.model.queue.MappingWorksheet.ExecutorSlot;
import hudson.model.queue.MappingWorksheet.Mapping;
import hudson.model.queue.MappingWorksheet.WorkChunk;
import hudson.util.TimeUnit2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Experimental.
 *
 * @author Kohsuke Kawaguchi
 */
public class BackFiller extends LoadPredictor {
    private boolean recursion = false;

    @Override
    public Iterable<FutureLoad> predict(MappingWorksheet plan, Computer computer, long start, long end) {
        TimeRange timeRange = new TimeRange(start, end - start);
        List<FutureLoad> loads = new ArrayList<FutureLoad>();

        for (BuildableItem bi : Jenkins.getInstance().getQueue().getBuildableItems()) {
            TentativePlan tp = bi.getAction(TentativePlan.class);
            if (tp==null) {// do this even for bi==plan.item ensures that we have FIFO semantics in tentative plans.
                tp = makeTentativePlan(bi);
                if (tp==null)   continue;   // no viable plan.
            }

            if (tp.isStale()) {
                // if the tentative plan is stale, just keep on pushing it to the current time
                // (if we recreate the plan, it'll be put at the end of the queue, whereas this job
                // should actually get priority over others)
                tp.range.shiftTo(System.currentTimeMillis());
            }

            // don't let its own tentative plan count when considering a scheduling for a job
            if (plan.item==bi)  continue;


            // no overlap in the time span, meaning this plan is for a distant future
            if (!timeRange.overlapsWith(tp.range)) continue;

            // if this tentative plan has no baring on this computer, that's ignorable
            Integer i = tp.footprint.get(computer);
            if (i==null)    continue;

            return Collections.singleton(tp.range.toFutureLoad(i));
        }

        return loads;
    }

    private static final class PseudoExecutorSlot extends ExecutorSlot {
        private Executor executor;

        private PseudoExecutorSlot(Executor executor) {
            this.executor = executor;
        }

        @Override
        public Executor getExecutor() {
            return executor;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        // this slot isn't executable
        @Override
        protected void set(WorkUnit p) {
            throw new UnsupportedOperationException();
        }
    }

    private TentativePlan makeTentativePlan(BuildableItem bi) {
        if (recursion)  return null;
        recursion = true;
        try {
            // pretend for now that all executors are available and decide some assignment that's executable.
            List<PseudoExecutorSlot> slots = new ArrayList<PseudoExecutorSlot>();
            for (Computer c : Jenkins.getInstance().getComputers()) {
                if (c.isOffline())  continue;
                for (Executor e : c.getExecutors()) {
                    slots.add(new PseudoExecutorSlot(e));
                }
            }

            // also ignore all load predictions as we just want to figure out some executable assignment
            // and we are not trying to figure out if this task is executable right now.
            MappingWorksheet worksheet = new MappingWorksheet(bi, slots, Collections.<LoadPredictor>emptyList());
            Mapping m = Jenkins.getInstance().getQueue().getLoadBalancer().map(bi.task, worksheet);
            if (m==null)    return null;

            // figure out how many executors we need on each computer?
            Map<Computer,Integer> footprint = new HashMap<Computer, Integer>();
            for (Entry<WorkChunk, ExecutorChunk> e : m.toMap().entrySet()) {
                Computer c = e.getValue().computer;
                Integer v = footprint.get(c);
                if (v==null)    v = 0;
                v += e.getKey().size();
                footprint.put(c,v);
            }

            // the point of a tentative plan is to displace other jobs to create a point in time
            // where this task can start executing. An incorrectly estimated duration is not
            // a problem in this regard, as we just need enough idle executors in the right moment.
            // The downside of guessing the duration wrong is that we can end up creating tentative plans
            // afterward that may be incorrect, but those plans will be rebuilt.
            long d = bi.task.getEstimatedDuration();
            if (d<=0)    d = TimeUnit2.MINUTES.toMillis(5);

            TimeRange slot = new TimeRange(System.currentTimeMillis(), d);

            // now, based on the real predicted loads, figure out the approximation of when we can
            // start executing this guy.
            for (Entry<Computer, Integer> e : footprint.entrySet()) {
                Computer computer = e.getKey();
                Timeline timeline = new Timeline();
                for (LoadPredictor lp : LoadPredictor.all()) {
                    for (FutureLoad fl : Iterables.limit(lp.predict(worksheet, computer, slot.start, slot.end),100)) {
                        timeline.insert(fl.startTime, fl.startTime+fl.duration, fl.numExecutors);
                    }
                }

                Long x = timeline.fit(slot.start, slot.duration, computer.countExecutors()-e.getValue());
                // if no suitable range was found in [slot.start,slot.end), slot.end would be a good approximation
                if (x==null)    x = slot.end;
                slot = slot.shiftTo(x);
            }

            TentativePlan tp = new TentativePlan(footprint, slot);
            bi.addAction(tp);
            return tp;
        } finally {
            recursion = false;
        }
    }

    /**
     * Represents a duration in time.
     */
    private static final class TimeRange {
        public final long start;
        public final long duration;
        public final long end;

        private TimeRange(long start, long duration) {
            this.start = start;
            this.duration = duration;
            this.end = start+duration;
        }

        public boolean overlapsWith(TimeRange that) {
            return (this.start <= that.start && that.start <=this.end)
                || (that.start <= this.start && this.start <=that.end);
        }

        public FutureLoad toFutureLoad(int size) {
            return new FutureLoad(start,duration,size);
        }

        public TimeRange shiftTo(long newStart) {
            if (newStart==start)    return this;
            return new TimeRange(newStart,duration);
        }
    }

    public static final class TentativePlan extends InvisibleAction {
        private final Map<Computer,Integer> footprint;
        public final TimeRange range;

        public TentativePlan(Map<Computer, Integer> footprint, TimeRange range) {
            this.footprint = footprint;
            this.range = range;
        }

        public Object writeReplace() {// don't persist
            return null;
        }

        public boolean isStale() {
            return range.end < System.currentTimeMillis();
        }
    }

    /**
     * Once this feature stabilizes, move it to the heavyjob plugin
     */
    @Extension
    public static BackFiller newInstance() {
        if (Boolean.getBoolean(BackFiller.class.getName()))
            return new BackFiller();
        return null;
    }
}
