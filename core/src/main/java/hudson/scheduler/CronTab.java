/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, InfraDNA, Inc.
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
import java.util.TimeZone;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Calendar.*;
import javax.annotation.CheckForNull;

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

    /**
     * Optional timezone string for calendar 
     */
    private @CheckForNull String specTimezone;

    public CronTab(String format) throws ANTLRException {
        this(format,null);
    }

    public CronTab(String format, Hash hash) throws ANTLRException {
        this(format,1,hash);
    }
    
    /**
     * @deprecated as of 1.448
     *      Use {@link #CronTab(String, int, Hash)}
     */
    @Deprecated
    public CronTab(String format, int line) throws ANTLRException {
        set(format, line, null);
    }

    /**
     * @param hash
     *      Used to spread out token like "@daily". Null to preserve the legacy behaviour
     *      of not spreading it out at all.
     */
    public CronTab(String format, int line, Hash hash) throws ANTLRException {
        this(format, line, hash, null);
    }

    /**
     * @param timezone
     *      Used to schedule cron in a different timezone. Null to use the default system 
     *      timezone
     * @since 1.615
     */
    public CronTab(String format, int line, Hash hash, @CheckForNull String timezone) throws ANTLRException {
        set(format, line, hash, timezone);
    }
    
    private void set(String format, int line, Hash hash) throws ANTLRException {
        set(format, line, hash, null);
    }

    /**
     * @since 1.615
     */
    private void set(String format, int line, Hash hash, String timezone) throws ANTLRException {
        CrontabLexer lexer = new CrontabLexer(new StringReader(format));
        lexer.setLine(line);
        CrontabParser parser = new CrontabParser(lexer);
        parser.setHash(hash);
        spec = format;
        specTimezone = timezone;

        parser.startRule(this);
        if((dayOfWeek&(1<<7))!=0) {
            dayOfWeek |= 1; // copy bit 7 over to bit 0
            dayOfWeek &= ~(1<<7); // clear bit 7 or CalendarField#ceil will return an invalid value 7
        }
    }


    /**
     * Returns true if the given calendar matches
     */
    boolean check(Calendar cal) {

        Calendar checkCal = cal;

        if(specTimezone != null && !specTimezone.isEmpty()) {
            Calendar tzCal = Calendar.getInstance(TimeZone.getTimeZone(specTimezone));
            tzCal.setTime(cal.getTime());
            checkCal = tzCal;
        }

        if(!checkBits(bits[0],checkCal.get(MINUTE)))
            return false;
        if(!checkBits(bits[1],checkCal.get(HOUR_OF_DAY)))
            return false;
        if(!checkBits(bits[2],checkCal.get(DAY_OF_MONTH)))
            return false;
        if(!checkBits(bits[3],checkCal.get(MONTH)+1))
            return false;
        if(!checkBits(dayOfWeek,checkCal.get(Calendar.DAY_OF_WEEK)-1))
            return false;

        return true;
    }

    private static abstract class CalendarField {
        /**
         * {@link Calendar} field ID.
         */
        final int field;
        /**
         * Lower field is a calendar field whose value needs to be reset when we change the value in this field.
         * For example, if we modify the value in HOUR, MINUTES must be reset.
         */
        final CalendarField lowerField;
        /**
         * Whether this field is 0-origin or 1-origin differs between Crontab and {@link Calendar},
         * so this field adjusts that. If crontab is 1 origin and calendar is 0 origin,  this field is 1
         * that is the value is {@code (cronOrigin-calendarOrigin)}
         */
        final int offset;
        /**
         * When we reset this field, we set the field to this value.
         * For example, resetting {@link Calendar#DAY_OF_MONTH} means setting it to 1.
         */
        final int min;
        /**
         * If this calendar field has other aliases such that a change in this field
         * modifies other field values, then true.
         */
        final boolean redoAdjustmentIfModified;

        /**
         * What is this field? Useful for debugging
         */
        @SuppressWarnings("unused")
        private final String displayName;

        private CalendarField(String displayName, int field, int min, int offset, boolean redoAdjustmentIfModified, CalendarField lowerField) {
            this.displayName = displayName;
            this.field = field;
            this.min = min;
            this.redoAdjustmentIfModified= redoAdjustmentIfModified;
            this.lowerField = lowerField;
            this.offset = offset;
        }

        /**
         * Gets the current value of this field in the given calendar.
         */
        int valueOf(Calendar c) {
            return c.get(field)+offset;
        }

        void addTo(Calendar c, int i) {
            c.add(field,i);
        }

        void setTo(Calendar c, int i) {
            c.set(field,i-offset);
        }

        void clear(Calendar c) {
            setTo(c, min);
        }

        /**
         * Given the value 'n' (which represents the current value), finds the smallest x such that:
         *  1) x matches the specified {@link CronTab} (as far as this field is concerned.)
         *  2) x>=n   (inclusive)
         *
         * If there's no such bit, return -1. Note that if 'n' already matches the crontab, the same n will be returned.
         */
        private int ceil(CronTab c, int n) {
            long bits = bits(c);
            while ((bits|(1L<<n))!=bits) {
                if (n>60)   return -1;
                n++;
            }
            return n;
        }

        /**
         * Given a bit mask, finds the first bit that's on, and return its index.
         */
        private int first(CronTab c) {
            return ceil(c,0);
        }

        private int floor(CronTab c, int n) {
            long bits = bits(c);
            while ((bits|(1L<<n))!=bits) {
                if (n==0)   return -1;
                n--;
            }
            return n;
        }

        private int last(CronTab c) {
            return floor(c,63);
        }

        /**
         * Extracts the bit masks from the given {@link CronTab} that matches this field.
         */
        abstract long bits(CronTab c);

        /**
         * Increment the next field.
         */
        abstract void rollUp(Calendar cal, int i);

        private static final CalendarField MINUTE       = new CalendarField("minute", Calendar.MINUTE,        0, 0, false, null) {
            long bits(CronTab c) { return c.bits[0]; }
            void rollUp(Calendar cal, int i) { cal.add(Calendar.HOUR_OF_DAY,i); }
        };
        private static final CalendarField HOUR         = new CalendarField("hour", Calendar.HOUR_OF_DAY,   0, 0, false, MINUTE) {
            long bits(CronTab c) { return c.bits[1]; }
            void rollUp(Calendar cal, int i) { cal.add(Calendar.DAY_OF_MONTH,i); }
        };
        private static final CalendarField DAY_OF_MONTH = new CalendarField("day", Calendar.DAY_OF_MONTH,  1, 0, true,  HOUR) {
            long bits(CronTab c) { return c.bits[2]; }
            void rollUp(Calendar cal, int i) { cal.add(Calendar.MONTH,i); }
        };
        private static final CalendarField MONTH        = new CalendarField("month", Calendar.MONTH,         1, 1, false, DAY_OF_MONTH) {
            long bits(CronTab c) { return c.bits[3]; }
            void rollUp(Calendar cal, int i) { cal.add(Calendar.YEAR,i); }
        };
        private static final CalendarField DAY_OF_WEEK  = new CalendarField("dow", Calendar.DAY_OF_WEEK,   1,-1, true,  HOUR) {
            long bits(CronTab c) { return c.dayOfWeek; }
            void rollUp(Calendar cal, int i) {
                cal.add(Calendar.DAY_OF_WEEK, 7 * i);
            }

            @Override
            void setTo(Calendar c, int i) {
                int v = i-offset;
                int was = c.get(field);
                c.set(field,v);
                final int firstDayOfWeek = c.getFirstDayOfWeek();
                if (v < firstDayOfWeek && was >= firstDayOfWeek) {
                    // in crontab, the first DoW is always Sunday, but in Java, it can be Monday or in theory arbitrary other days.
                    // When first DoW is 1/2 Monday, calendar points to 1/2 Monday, setting the DoW to Sunday makes
                    // the calendar moves forward to 1/8 Sunday, instead of 1/1 Sunday. So we need to compensate that effect here.
                    addTo(c,-7);
                } else if (was < firstDayOfWeek && firstDayOfWeek <= v) {
                    // If we wrap the other way around, we need to adjust in the opposite direction of above.
                    addTo(c, 7);
                }
            }
        };

        private static final CalendarField[] ADJUST_ORDER = {
            MONTH, DAY_OF_MONTH, DAY_OF_WEEK, HOUR, MINUTE
        };
    }


    /**
     * Computes the nearest future timestamp that matches this cron tab.
     * <p>
     * More precisely, given the time 't', computes another smallest time x such that:
     *
     * <ul>
     * <li>x >= t (inclusive)
     * <li>x matches this crontab
     * </ul>
     *
     * <p>
     * Note that if t already matches this cron, it's returned as is.
     */
    public Calendar ceil(long t) {
        Calendar cal = new GregorianCalendar(Locale.US);
        cal.setTimeInMillis(t);
        return ceil(cal);
    }

    /**
     * See {@link #ceil(long)}.
     *
     * This method modifies the given calendar and returns the same object.
     */
    public Calendar ceil(Calendar cal) {
        OUTER:
        while (true) {
            for (CalendarField f : CalendarField.ADJUST_ORDER) {
                int cur = f.valueOf(cal);
                int next = f.ceil(this,cur);
                if (cur==next)  continue;   // this field is already in a good shape. move on to next

                // we are modifying this field, so clear all the lower level fields
                for (CalendarField l=f.lowerField; l!=null; l=l.lowerField)
                    l.clear(cal);

                if (next<0) {
                    // we need to roll over to the next field.
                    f.rollUp(cal, 1);
                    f.setTo(cal,f.first(this));
                    // since higher order field is affected by this, we need to restart from all over
                    continue OUTER;
                } else {
                    f.setTo(cal,next);
                    if (f.redoAdjustmentIfModified)
                        continue OUTER; // when we modify DAY_OF_MONTH and DAY_OF_WEEK, do it all over from the top
                }
            }
            return cal; // all fields adjusted
        }
    }

    /**
     * Computes the nearest past timestamp that matched this cron tab.
     * <p>
     * More precisely, given the time 't', computes another smallest time x such that:
     *
     * <ul>
     * <li>x &lt;= t (inclusive)
     * <li>x matches this crontab
     * </ul>
     *
     * <p>
     * Note that if t already matches this cron, it's returned as is.
     */
    public Calendar floor(long t) {
        Calendar cal = new GregorianCalendar(Locale.US);
        cal.setTimeInMillis(t);
        return floor(cal);
    }

    /**
     * See {@link #floor(long)}
     *
     * This method modifies the given calendar and returns the same object.
     */
    public Calendar floor(Calendar cal) {
        OUTER:
        while (true) {
            for (CalendarField f : CalendarField.ADJUST_ORDER) {
                int cur = f.valueOf(cal);
                int next = f.floor(this,cur);
                if (cur==next)  continue;   // this field is already in a good shape. move on to next

                // we are modifying this field, so clear all the lower level fields
                for (CalendarField l=f.lowerField; l!=null; l=l.lowerField)
                    l.clear(cal);

                if (next<0) {
                    // we need to borrow from the next field.
                    f.rollUp(cal,-1);
                    // the problem here, in contrast with the ceil method, is that
                    // the maximum value of the field is not always a fixed value (that is, day of month)
                    // so we zero-clear all the lower fields, set the desired value +1,
                    f.setTo(cal,f.last(this));
                    f.addTo(cal,1);
                    // then subtract a minute to achieve maximum values on all the lower fields,
                    // with the desired value in 'f'
                    CalendarField.MINUTE.addTo(cal,-1);
                    // since higher order field is affected by this, we need to restart from all over
                    continue OUTER;
                } else {
                    f.setTo(cal,next);
                    f.addTo(cal,1);
                    CalendarField.MINUTE.addTo(cal,-1);
                    if (f.redoAdjustmentIfModified)
                        continue OUTER; // when we modify DAY_OF_MONTH and DAY_OF_WEEK, do it all over from the top
                }
            }
            return cal; // all fields adjusted
        }
    }

    void set(String format, Hash hash) throws ANTLRException {
        set(format,1,hash);
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
    public @CheckForNull String checkSanity() {
        OUTER: for (int i = 0; i < 5; i++) {
            long bitMask = (i<4)?bits[i]:(long)dayOfWeek;
            for( int j=BaseParser.LOWER_BOUNDS[i]; j<=BaseParser.UPPER_BOUNDS[i]; j++ ) {
                if(!checkBits(bitMask,j)) {
                    // this rank has a sparse entry.
                    // if we have a sparse rank, one of them better be the left-most.
                    if(i>0)
                        return Messages.CronTab_do_you_really_mean_every_minute_when_you(spec, "H " + spec.substring(spec.indexOf(' ') + 1));
                    // once we find a sparse rank, upper ranks don't matter
                    break OUTER;
                }
            }
        }

        int daysOfMonth = 0;
        for (int i = 1; i < 31; i++) {
            if (checkBits(bits[2], i)) {
                daysOfMonth++;
            }
        }
        if (daysOfMonth > 5 && daysOfMonth < 28) { // a bit arbitrary
            return Messages.CronTab_short_cycles_in_the_day_of_month_field_w();
        }

        String hashified = hashify(spec);
        if (hashified != null) {
            return Messages.CronTab_spread_load_evenly_by_using_rather_than_(hashified, spec);
        }

        return null;
    }

    /**
     * Checks a prospective crontab specification to see if it could benefit from balanced hashes.
     * @param spec a (legal) spec
     * @return a similar spec that uses a hash, if such a transformation is necessary; null if it is OK as is
     * @since 1.510
     */
    public static @CheckForNull String hashify(String spec) {
        if (spec.contains("H")) {
            // if someone is already using H, presumably he knows what it is, so a warning is likely false positive
            return null;
        } else if (spec.startsWith("*/")) {// "*/15 ...." (every N minutes) to hash
            return "H" + spec.substring(1);
        } else if (spec.matches("\\d+ .+")) {// "0 ..." (certain minute) to hash
            return "H " + spec.substring(spec.indexOf(' ') + 1);
        } else {
            Matcher m = Pattern.compile("0(,(\\d+)(,\\d+)*)( .+)").matcher(spec);
            if (m.matches()) { // 0,15,30,45 to H/15
                int period = Integer.parseInt(m.group(2));
                if (period > 0) {
                    StringBuilder b = new StringBuilder();
                    for (int i = period; i < 60; i += period) {
                        b.append(',').append(i);
                    }
                    if (b.toString().equals(m.group(1))) {
                        return "H/" + period + m.group(4);
                    }
                }
            }
            return null;
        }
    }
}
