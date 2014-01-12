/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.util

import hudson.model.Computer
import jenkins.util.Timer
import jenkins.model.Jenkins
import hudson.model.Label
import hudson.model.Queue.BlockedItem
import hudson.model.Queue.BuildableItem
import hudson.model.Queue.WaitingItem
import hudson.triggers.SafeTimerTask
import java.text.DateFormat
import java.util.concurrent.TimeUnit

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
        labels = Jenkins.getInstance().labels*.name;
        printHeaders();
        Timer.get().scheduleAtFixedRate(this,0,10*1000, TimeUnit.MILLISECONDS);
    }

    private String quote(Object s) { "\"${s}\""; }

    protected void printHeaders() {
        def headers = ["# of executors","# of busy executors","BuildableItems in Q","BuildableItem avg wait time"];
        def data = ["timestamp"];
        data += headers;
        data += ["WaitingItems in Q","BlockedItems in Q"];

        for( String label : labels)
            data += headers.collect { "${it} (${label}}" }

        dataFile.append(data.collect({ quote(it) }).join(",")+"\n");
    }

    @Override
    protected void doRun() {
        def now = new Date();
        def data = [];
        data.add(quote(FORMATTER.format(now)));

        def h = Jenkins.getInstance();

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

