package hudson.util;

import hudson.Util;

/**
 * Represents a clock difference. Immutable.
 *
 * @author Kohsuke Kawaguchi
 */
public final class ClockDifference {
    /**
     * The difference in milliseconds.
     *
     * Positive value means the slave is behind the master,
     * negative value means the slave is ahead of the master.
     */
    public final Long diff;

    public ClockDifference(long value) {
        this.diff = value;
    }

    /**
     * Returns true if the difference is big enough to be considered dangerous.
     */
    public boolean isDangerous() {
        return Math.abs(diff)>5000;
    }

    /**
     * Gets the clock difference in HTML string.
     */
    public String toString() {
        if(-1000<diff && diff <1000)
            return "In sync";  // clock is in sync

        long abs = Math.abs(diff);

        String s = Util.getTimeSpanString(abs);
        if(diff<0)
            s += " ahead";
        else
            s += " behind";

        return s;
    }
    
    public String toHtml() {
        String s = toString();
        if(isDangerous())   s = "<span class=error>"+s+"</span>";
        return s;
    }
}
