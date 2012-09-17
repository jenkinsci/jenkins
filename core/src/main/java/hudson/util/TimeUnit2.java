/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Stephen Connolly
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
/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package hudson.util;

import java.util.concurrent.TimeUnit;

/**
 * A <tt>TimeUnit</tt> represents time durations at a given unit of
 * granularity and provides utility methods to convert across units,
 * and to perform timing and delay operations in these units.  A
 * <tt>TimeUnit</tt> does not maintain time information, but only
 * helps organize and use time representations that may be maintained
 * separately across various contexts.  A nanosecond is defined as one
 * thousandth of a microsecond, a microsecond as one thousandth of a
 * millisecond, a millisecond as one thousandth of a second, a minute
 * as sixty seconds, an hour as sixty minutes, and a day as twenty four
 * hours.
 *
 * <p>A <tt>TimeUnit</tt> is mainly used to inform time-based methods
 * how a given timing parameter should be interpreted. For example,
 * the following code will timeout in 50 milliseconds if the {@link
 * java.util.concurrent.locks.Lock lock} is not available:
 *
 * <pre>  Lock lock = ...;
 *  if ( lock.tryLock(50L, TimeUnit.MILLISECONDS) ) ...
 * </pre>
 * while this code will timeout in 50 seconds:
 * <pre>
 *  Lock lock = ...;
 *  if ( lock.tryLock(50L, TimeUnit.SECONDS) ) ...
 * </pre>
 *
 * Note however, that there is no guarantee that a particular timeout
 * implementation will be able to notice the passage of time at the
 * same granularity as the given <tt>TimeUnit</tt>.
 *
 * @since 1.5
 * @author Doug Lea
 */
public enum TimeUnit2 {
    NANOSECONDS {
        @Override public long toNanos(long d)   { return d; }
        @Override public long toMicros(long d)  { return d/(C1/C0); }
        @Override public long toMillis(long d)  { return d/(C2/C0); }
        @Override public long toSeconds(long d) { return d/(C3/C0); }
        @Override public long toMinutes(long d) { return d/(C4/C0); }
        @Override public long toHours(long d)   { return d/(C5/C0); }
        @Override public long toDays(long d)    { return d/(C6/C0); }
        @Override public long convert(long d, TimeUnit2 u) { return u.toNanos(d); }
        @Override public long convert(long d, TimeUnit u) { return u.toNanos(d); }
        int excessNanos(long d, long m) { return (int)(d - (m*C2)); }
    },
    MICROSECONDS {
        @Override public long toNanos(long d)   { return x(d, C1/C0, MAX/(C1/C0)); }
        @Override public long toMicros(long d)  { return d; }
        @Override public long toMillis(long d)  { return d/(C2/C1); }
        @Override public long toSeconds(long d) { return d/(C3/C1); }
        @Override public long toMinutes(long d) { return d/(C4/C1); }
        @Override public long toHours(long d)   { return d/(C5/C1); }
        @Override public long toDays(long d)    { return d/(C6/C1); }
        @Override public long convert(long d, TimeUnit2 u) { return u.toMicros(d); }
        @Override public long convert(long d, TimeUnit u) { return u.toMicros(d); }
        int excessNanos(long d, long m) { return (int)((d*C1) - (m*C2)); }
    },
    MILLISECONDS {
        @Override public long toNanos(long d)   { return x(d, C2/C0, MAX/(C2/C0)); }
        @Override public long toMicros(long d)  { return x(d, C2/C1, MAX/(C2/C1)); }
        @Override public long toMillis(long d)  { return d; }
        @Override public long toSeconds(long d) { return d/(C3/C2); }
        @Override public long toMinutes(long d) { return d/(C4/C2); }
        @Override public long toHours(long d)   { return d/(C5/C2); }
        @Override public long toDays(long d)    { return d/(C6/C2); }
        @Override public long convert(long d, TimeUnit2 u) { return u.toMillis(d); }
        @Override public long convert(long d, TimeUnit u) { return u.toMillis(d); }
        int excessNanos(long d, long m) { return 0; }
    },
    SECONDS {
        @Override public long toNanos(long d)   { return x(d, C3/C0, MAX/(C3/C0)); }
        @Override public long toMicros(long d)  { return x(d, C3/C1, MAX/(C3/C1)); }
        @Override public long toMillis(long d)  { return x(d, C3/C2, MAX/(C3/C2)); }
        @Override public long toSeconds(long d) { return d; }
        @Override public long toMinutes(long d) { return d/(C4/C3); }
        @Override public long toHours(long d)   { return d/(C5/C3); }
        @Override public long toDays(long d)    { return d/(C6/C3); }
        @Override public long convert(long d, TimeUnit2 u) { return u.toSeconds(d); }
        @Override public long convert(long d, TimeUnit u) { return u.toSeconds(d); }
        int excessNanos(long d, long m) { return 0; }
    },
    MINUTES {
        @Override public long toNanos(long d)   { return x(d, C4/C0, MAX/(C4/C0)); }
        @Override public long toMicros(long d)  { return x(d, C4/C1, MAX/(C4/C1)); }
        @Override public long toMillis(long d)  { return x(d, C4/C2, MAX/(C4/C2)); }
        @Override public long toSeconds(long d) { return x(d, C4/C3, MAX/(C4/C3)); }
        @Override public long toMinutes(long d) { return d; }
        @Override public long toHours(long d)   { return d/(C5/C4); }
        @Override public long toDays(long d)    { return d/(C6/C4); }
        @Override public long convert(long d, TimeUnit2 u) { return u.toMinutes(d); }
        @Override public long convert(long d, TimeUnit u) { return SECONDS.toMinutes(u.toSeconds(d)); }
        int excessNanos(long d, long m) { return 0; }
    },
    HOURS {
        @Override public long toNanos(long d)   { return x(d, C5/C0, MAX/(C5/C0)); }
        @Override public long toMicros(long d)  { return x(d, C5/C1, MAX/(C5/C1)); }
        @Override public long toMillis(long d)  { return x(d, C5/C2, MAX/(C5/C2)); }
        @Override public long toSeconds(long d) { return x(d, C5/C3, MAX/(C5/C3)); }
        @Override public long toMinutes(long d) { return x(d, C5/C4, MAX/(C5/C4)); }
        @Override public long toHours(long d)   { return d; }
        @Override public long toDays(long d)    { return d/(C6/C5); }
        @Override public long convert(long d, TimeUnit2 u) { return u.toHours(d); }
        @Override public long convert(long d, TimeUnit u) { return SECONDS.toHours(u.toSeconds(d)); }
        int excessNanos(long d, long m) { return 0; }
    },
    DAYS {
        @Override public long toNanos(long d)   { return x(d, C6/C0, MAX/(C6/C0)); }
        @Override public long toMicros(long d)  { return x(d, C6/C1, MAX/(C6/C1)); }
        @Override public long toMillis(long d)  { return x(d, C6/C2, MAX/(C6/C2)); }
        @Override public long toSeconds(long d) { return x(d, C6/C3, MAX/(C6/C3)); }
        @Override public long toMinutes(long d) { return x(d, C6/C4, MAX/(C6/C4)); }
        @Override public long toHours(long d)   { return x(d, C6/C5, MAX/(C6/C5)); }
        @Override public long toDays(long d)    { return d; }
        @Override public long convert(long d, TimeUnit2 u) { return u.toDays(d); }
        @Override public long convert(long d, TimeUnit u) { return SECONDS.toDays(u.toSeconds(d)); }
        int excessNanos(long d, long m) { return 0; }
    };

    // Handy constants for conversion methods
    static final long C0 = 1L;
    static final long C1 = C0 * 1000L;
    static final long C2 = C1 * 1000L;
    static final long C3 = C2 * 1000L;
    static final long C4 = C3 * 60L;
    static final long C5 = C4 * 60L;
    static final long C6 = C5 * 24L;

    static final long MAX = Long.MAX_VALUE;

    /**
     * Scale d by m, checking for overflow.
     * This has a short name to make above code more readable.
     */
    static long x(long d, long m, long over) {
        if (d >  over) return Long.MAX_VALUE;
        if (d < -over) return Long.MIN_VALUE;
        return d * m;
    }

    // To maintain full signature compatibility with 1.5, and to improve the
    // clarity of the generated javadoc (see 6287639: Abstract methods in
    // enum classes should not be listed as abstract), method convert
    // etc. are not declared abstract but otherwise act as abstract methods.

    /**
     * Convert the given time duration in the given unit to this
     * unit.  Conversions from finer to coarser granularities
     * truncate, so lose precision. For example converting
     * <tt>999</tt> milliseconds to seconds results in
     * <tt>0</tt>. Conversions from coarser to finer granularities
     * with arguments that would numerically overflow saturate to
     * <tt>Long.MIN_VALUE</tt> if negative or <tt>Long.MAX_VALUE</tt>
     * if positive.
     *
     * <p>For example, to convert 10 minutes to milliseconds, use:
     * <tt>TimeUnit.MILLISECONDS.convert(10L, TimeUnit.MINUTES)</tt>
     *
     * @param sourceDuration the time duration in the given <tt>sourceUnit</tt>
     * @param sourceUnit the unit of the <tt>sourceDuration</tt> argument
     * @return the converted duration in this unit,
     * or <tt>Long.MIN_VALUE</tt> if conversion would negatively
     * overflow, or <tt>Long.MAX_VALUE</tt> if it would positively overflow.
     */
    public long convert(long sourceDuration, TimeUnit2 sourceUnit) {
        throw new AbstractMethodError();
    }

    /**
     * Convert the given time duration in the given unit to this
     * unit.  Conversions from finer to coarser granularities
     * truncate, so lose precision. For example converting
     * <tt>999</tt> milliseconds to seconds results in
     * <tt>0</tt>. Conversions from coarser to finer granularities
     * with arguments that would numerically overflow saturate to
     * <tt>Long.MIN_VALUE</tt> if negative or <tt>Long.MAX_VALUE</tt>
     * if positive.
     *
     * <p>For example, to convert 10 minutes to milliseconds, use:
     * <tt>TimeUnit.MILLISECONDS.convert(10L, TimeUnit.MINUTES)</tt>
     *
     * @param sourceDuration the time duration in the given <tt>sourceUnit</tt>
     * @param sourceUnit the unit of the <tt>sourceDuration</tt> argument
     * @return the converted duration in this unit,
     * or <tt>Long.MIN_VALUE</tt> if conversion would negatively
     * overflow, or <tt>Long.MAX_VALUE</tt> if it would positively overflow.
     */
    public long convert(long sourceDuration, TimeUnit sourceUnit) {
        throw new AbstractMethodError();
    }

    /**
     * Equivalent to <tt>NANOSECONDS.convert(duration, this)</tt>.
     * @param duration the duration
     * @return the converted duration,
     * or <tt>Long.MIN_VALUE</tt> if conversion would negatively
     * overflow, or <tt>Long.MAX_VALUE</tt> if it would positively overflow.
     * @see #convert
     */
    public long toNanos(long duration) {
        throw new AbstractMethodError();
    }

    /**
     * Equivalent to <tt>MICROSECONDS.convert(duration, this)</tt>.
     * @param duration the duration
     * @return the converted duration,
     * or <tt>Long.MIN_VALUE</tt> if conversion would negatively
     * overflow, or <tt>Long.MAX_VALUE</tt> if it would positively overflow.
     * @see #convert
     */
    public long toMicros(long duration) {
        throw new AbstractMethodError();
    }

    /**
     * Equivalent to <tt>MILLISECONDS.convert(duration, this)</tt>.
     * @param duration the duration
     * @return the converted duration,
     * or <tt>Long.MIN_VALUE</tt> if conversion would negatively
     * overflow, or <tt>Long.MAX_VALUE</tt> if it would positively overflow.
     * @see #convert
     */
    public long toMillis(long duration) {
        throw new AbstractMethodError();
    }

    /**
     * Equivalent to <tt>SECONDS.convert(duration, this)</tt>.
     * @param duration the duration
     * @return the converted duration,
     * or <tt>Long.MIN_VALUE</tt> if conversion would negatively
     * overflow, or <tt>Long.MAX_VALUE</tt> if it would positively overflow.
     * @see #convert
     */
    public long toSeconds(long duration) {
        throw new AbstractMethodError();
    }

    /**
     * Equivalent to <tt>MINUTES.convert(duration, this)</tt>.
     * @param duration the duration
     * @return the converted duration,
     * or <tt>Long.MIN_VALUE</tt> if conversion would negatively
     * overflow, or <tt>Long.MAX_VALUE</tt> if it would positively overflow.
     * @see #convert
     * @since 1.6
     */
    public long toMinutes(long duration) {
        throw new AbstractMethodError();
    }

    /**
     * Equivalent to <tt>HOURS.convert(duration, this)</tt>.
     * @param duration the duration
     * @return the converted duration,
     * or <tt>Long.MIN_VALUE</tt> if conversion would negatively
     * overflow, or <tt>Long.MAX_VALUE</tt> if it would positively overflow.
     * @see #convert
     * @since 1.6
     */
    public long toHours(long duration) {
        throw new AbstractMethodError();
    }

    /**
     * Equivalent to <tt>DAYS.convert(duration, this)</tt>.
     * @param duration the duration
     * @return the converted duration
     * @see #convert
     * @since 1.6
     */
    public long toDays(long duration) {
        throw new AbstractMethodError();
    }

    /**
     * Utility to compute the excess-nanosecond argument to wait,
     * sleep, join.
     * @param d the duration
     * @param m the number of milliseconds
     * @return the number of nanoseconds
     */
    abstract int excessNanos(long d, long m);

    /**
     * Performs a timed <tt>Object.wait</tt> using this time unit.
     * This is a convenience method that converts timeout arguments
     * into the form required by the <tt>Object.wait</tt> method.
     *
     * <p>For example, you could implement a blocking <tt>poll</tt>
     * method (see {@link java.util.concurrent.BlockingQueue#poll BlockingQueue.poll})
     * using:
     *
     * <pre>  public synchronized Object poll(long timeout, TimeUnit unit) throws InterruptedException {
     *    while (empty) {
     *      unit.timedWait(this, timeout);
     *      ...
     *    }
     *  }</pre>
     *
     * @param obj the object to wait on
     * @param timeout the maximum time to wait. If less than
     * or equal to zero, do not wait at all.
     * @throws InterruptedException if interrupted while waiting.
     * @see Object#wait(long, int)
     */
    public void timedWait(Object obj, long timeout)
    throws InterruptedException {
        if (timeout > 0) {
            long ms = toMillis(timeout);
            int ns = excessNanos(timeout, ms);
            obj.wait(ms, ns);
        }
    }

    /**
     * Performs a timed <tt>Thread.join</tt> using this time unit.
     * This is a convenience method that converts time arguments into the
     * form required by the <tt>Thread.join</tt> method.
     * @param thread the thread to wait for
     * @param timeout the maximum time to wait. If less than
     * or equal to zero, do not wait at all.
     * @throws InterruptedException if interrupted while waiting.
     * @see Thread#join(long, int)
     */
    public void timedJoin(Thread thread, long timeout)
    throws InterruptedException {
        if (timeout > 0) {
            long ms = toMillis(timeout);
            int ns = excessNanos(timeout, ms);
            thread.join(ms, ns);
        }
    }

    /**
     * Performs a <tt>Thread.sleep</tt> using this unit.
     * This is a convenience method that converts time arguments into the
     * form required by the <tt>Thread.sleep</tt> method.
     * @param timeout the minimum time to sleep. If less than
     * or equal to zero, do not sleep at all.
     * @throws InterruptedException if interrupted while sleeping.
     * @see Thread#sleep
     */
    public void sleep(long timeout) throws InterruptedException {
        if (timeout > 0) {
            long ms = toMillis(timeout);
            int ns = excessNanos(timeout, ms);
            Thread.sleep(ms, ns);
        }
    }

}