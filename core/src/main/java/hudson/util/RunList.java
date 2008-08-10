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
            if (r instanceof AbstractBuild) {
            	if (!((AbstractBuild) r).getBuiltOn().equals(node)) {
                    itr.remove();
            	}
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
