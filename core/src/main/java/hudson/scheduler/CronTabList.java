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

    /**
     * Returns true if the given calendar matches
     */
    public synchronized boolean check(Calendar cal) {
        for (CronTab tab : tabs) {
            if(tab.check(cal))
                return true;
        }
        return false;
    }

    /**
     * Checks if this crontab entry looks reasonable,
     * and if not, return an warning message.
     *
     * <p>
     * The point of this method is to catch syntactically correct
     * but semantically suspicious combinations, like
     * "* 0 * * *"
     */
    public String checkSanity() {
        for (CronTab tab : tabs) {
            String s = tab.checkSanity();
            if(s!=null)     return s;
        }
        return null;
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
                throw new ANTLRException(Messages.CronTabList_InvalidInput(line,e.toString()),e);
            }
        }
        return new CronTabList(r);
    }
}
