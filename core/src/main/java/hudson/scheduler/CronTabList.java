package hudson.scheduler;

import antlr.ANTLRException;

import java.util.Calendar;
import java.util.Collection;
import java.util.Vector;

/**
 * {@link CronTab} list (logically OR-ed).
 *
 * @author Kohsuke Kawaguchi
 */
public final class CronTabList {
    private final Vector<CronTab> tabs;

    public CronTabList(Collection<CronTab> tabs) {
        this.tabs = new Vector<CronTab>(tabs);
    }

    public synchronized boolean check(Calendar cal) {
        for (CronTab tab : tabs) {
            if(tab.check(cal))
                return true;
        }
        return false;
    }

    public static CronTabList create(String format) throws ANTLRException {
        Vector<CronTab> r = new Vector<CronTab>();
        int lineNumber = 0;
        for (String line : format.split("\\r?\\n")) {
            lineNumber++;
            line = line.trim();
            if(line.length()==0 || line.startsWith("#"))
                continue;   // ignorable line
            try {
                r.add(new CronTab(line,lineNumber));
            } catch (ANTLRException e) {
                throw new ANTLRException("Invalid input: \""+line+"\": "+e.toString(),e);
            }
        }
        return new CronTabList(r);
    }
}
