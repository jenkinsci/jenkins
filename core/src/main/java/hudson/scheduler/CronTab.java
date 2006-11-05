package hudson.scheduler;

import antlr.ANTLRException;

import java.io.StringReader;
import java.util.Calendar;

/**
 * Table for driving scheduled tasks.
 *
 * @author Kohsuke Kawaguchi
 */
public final class CronTab {
    /**
     * bits[0]: minutes
     * bits[1]: hours
     * bits[2]: days
     * bits[3]: months
     *
     * false:not scheduled &lt;-> true scheduled
     */
    final long[] bits = new long[4];

    int dayOfWeek;

    public CronTab(String format) throws ANTLRException {
        this(format,1);
    }

    public CronTab(String format, int line) throws ANTLRException {
        CrontabLexer lexer = new CrontabLexer(new StringReader(format));
        lexer.setLine(line);
        CrontabParser parser = new CrontabParser(lexer);

        parser.startRule(this);
        if((dayOfWeek&(1<<7))!=0)
            dayOfWeek |= 1; // copy bit 7 over to bit 0
    }


    /**
     * Returns true if the given calendar matches
     */
    boolean check(Calendar cal) {
        if(!checkBits(bits[0],cal.get(Calendar.MINUTE)))
            return false;
        if(!checkBits(bits[1],cal.get(Calendar.HOUR_OF_DAY)))
            return false;
        if(!checkBits(bits[2],cal.get(Calendar.DAY_OF_MONTH)))
            return false;
        if(!checkBits(bits[3],cal.get(Calendar.MONTH)+1))
            return false;
        if(!checkBits(dayOfWeek,cal.get(Calendar.DAY_OF_WEEK)-1))
            return false;

        return true;
    }

    /**
     * Returns true if n-th bit is on.
     */
    private boolean checkBits(long bitMask, int n) {
        return (bitMask|(1L<<n))==bitMask;
    }

    public String toString() {
        return super.toString()+"["+
            toString("minute",bits[0])+','+
            toString("hour",bits[1])+','+
            toString("dayOfMonth",bits[2])+','+
            toString("month",bits[3])+','+
            toString("dayOfWeek",dayOfWeek)+']';
    }

    private String toString(String key, long bit) {
        return key+'='+Long.toHexString(bit);
    }
}
