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
package hudson.util;

import hudson.model.AbstractBuild;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.View;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;

/**
 * {@link List} of {@link Run}s.
 *
 * @author Kohsuke Kawaguchi
 */
public class RunList extends ArrayList<Run> {

    public RunList() {
    }

    public RunList(Job<?,?> j) {
        addAll(j.getBuilds());
    }

    public RunList(View view) {
        for (Item item : view.getItems())
            for (Job<?,?> j : item.getAllJobs())
                addAll(j.getBuilds());
        Collections.sort(this,Run.ORDER_BY_DATE);
    }

    public RunList(Collection<? extends Job> jobs) {
        for (Job<?,?> j : jobs)
            addAll(j.getBuilds());
        Collections.sort(this,Run.ORDER_BY_DATE);
    }

    public static RunList fromRuns(Collection<? extends Run> runs) {
        RunList r = new RunList();
        r.addAll(runs);
        return r;
    }

    /**
     * Filter the list to non-successful builds only.
     */
    public RunList failureOnly() {
        for (Iterator<Run> itr = iterator(); itr.hasNext();) {
            Run r = itr.next();
            if(r.getResult()==Result.SUCCESS)
                itr.remove();
        }
        return this;
    }

    /**
     * Filter the list to builds on a single node only
     */
    public RunList node(Node node) {
        for (Iterator<Run> itr = iterator(); itr.hasNext();) {
            Run r = itr.next();
            if (!(r instanceof AbstractBuild) || ((AbstractBuild)r).getBuiltOn()!=node) {
                itr.remove();
            }
        }
        return this;
    }

    /**
     * Filter the list to regression builds only.
     */
    public RunList regressionOnly() {
        for (Iterator<Run> itr = iterator(); itr.hasNext();) {
            Run r = itr.next();
            if(!r.getBuildStatusSummary().isWorse)
                itr.remove();
        }
        return this;
    }

    /**
     * Reduce the size of the list by only leaving relatively new ones.
     * This also removes on-going builds, as RSS cannot be used to publish information
     * if it changes.
     */
    public RunList newBuilds() {
        GregorianCalendar threshold = new GregorianCalendar();
        threshold.add(Calendar.DAY_OF_YEAR,-7);

        int count=0;

        for (Iterator<Run> itr = iterator(); itr.hasNext();) {
            Run r = itr.next();
            if(r.isBuilding()) {
                // can't publish on-going builds
                itr.remove();
                continue;
            }
            // at least put 10 items
            if(count<10) {
                count++;
                continue;
            }
            // anything older than 7 days will be ignored
            if(r.getTimestamp().before(threshold))
                itr.remove();
        }
        return this;
    }
}
