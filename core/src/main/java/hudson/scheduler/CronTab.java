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

    /**
     * Textual representation.
     */
    private String spec;

    public CronTab(String format) throws ANTLRException {
        this(format,1);
    }

    public CronTab(String format, int line) throws ANTLRException {
        set(format, line);
    }

    private void set(String format, int line) throws ANTLRException {
        CrontabLexer lexer = new CrontabLexer(new StringReader(format));
        lexer.setLine(line);
        CrontabParser parser = new CrontabParser(lexer);
        spec = format;

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

    void set(String format) throws ANTLRException {
        set(format,1);
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
        for( int i=0; i<5; i++ ) {
            long bitMask = (i<4)?bits[i]:(long)dayOfWeek;
            for( int j=LOWER_BOUNDS[i]; j<=UPPER_BOUNDS[i]; j++ ) {
                if(!checkBits(bitMask,j)) {
                    // this rank has a sparse entry.
                    // if we have a sparse rank, one of them better be the left-most.
                    if(i>0)
                        return "Do you really mean \"every minute\" when you say \""+spec+"\"? "+
                                "Perhaps you meant \"0 "+spec.substring(spec.indexOf(' ')+1)+"\"";
                    // once we find a sparse rank, upper ranks don't matter
                    return null;
                }
            }
        }

        return null;
    }

    // lower/uppser bounds of fields
    private static final int[] LOWER_BOUNDS = new int[] {0,0,1,0,0};
    private static final int[] UPPER_BOUNDS = new int[] {59,23,31,12,7};
}
