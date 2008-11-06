package hudson.util

import hudson.model.Computer
import hudson.model.Hudson
import hudson.model.Label
import hudson.model.Queue.BlockedItem
import hudson.model.Queue.BuildableItem
import hudson.model.Queue.WaitingItem
import hudson.triggers.SafeTimerTask
import java.text.DateFormat
import hudson.triggers.Trigger;

/**
 * Spits out the load information.
 *
 * <p>
 * I'm using this code to design the auto scaling feature.
 * In future this might be useful data to expose to the UI.
 *
 * @author Kohsuke Kawaguchi
 */
public class LoadMonitorImpl extends SafeTimerTask {

    private final File dataFile;
    private List<String> labels;

    public LoadMonitorImpl(File dataFile) {
        this.dataFile = dataFile;
        labels = Hudson.getInstance().labels*.name;
        printHeaders();
        Trigger.timer.scheduleAtFixedRate(this,0,10*1000);
    }

    protected void printHeaders() {
        def headers = ["# of executors","# of busy executors","BuildableItems in Q","BuildableItem avg wait time"];
        def data = ["timestamp"];
        data += headers;
        data += ["WaitingItems in Q","BlockedItems in Q"];

        for( String label : labels)
            data += headers.collect { "${it} (${label}}" }

        dataFile.append(data.collect({ "\"${it}\"" }).join(",")+"\n");
    }

    @Override
    protected void doRun() {
        def now = new Date();
        def data = [];
        data.add(FORMATTER.format(now));

        def h = Hudson.getInstance();

        def items = h.queue.items;
        def filterByType = {Class type -> items.findAll { type.isInstance(it) } }

        def builder = {List<Computer> cs, Closure itemFilter ->
            // number of total executor, number of busy executor
            data.add(cs.sum { it.isOffline() ? 0 : it.numExecutors });
            data.add(cs.sum {Computer c ->
                c.executors.findAll { !it.isIdle() }.size()
            });

            // queue statistics
            def is = filterByType(BuildableItem).findAll(itemFilter);
            data.add(is.size());
            data.add(is.sum {BuildableItem bi -> now.time - bi.buildableStartMilliseconds }?:0 / Math.max(1,is.size()) );
        };


        // for the whole thing
        builder(Arrays.asList(h.computers),{ it->true });

        data.add(filterByType(WaitingItem).size());
        data.add(filterByType(BlockedItem).size());

        // per label stats
        for (String label : labels) {
            Label l = h.getLabel(label)
            builder(l.nodes.collect { it.toComputer() }) { BuildableItem bi -> bi.task.assignedLabel==l };
        }

        dataFile.append(data.join(",")+"\n");
    }

    private static final DateFormat FORMATTER = DateFormat.getDateTimeInstance();
}

new LoadMonitorImpl(new File("/files/hudson/load.txt"));

