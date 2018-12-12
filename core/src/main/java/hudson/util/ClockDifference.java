/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Seiji Sogabe, Thomas J. Black
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

import hudson.Util;
import hudson.model.Node;

import java.io.IOException;

import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.export.Exported;

/**
 * Represents a clock difference. Immutable.
 *
 * @author Kohsuke Kawaguchi
 */
@ExportedBean
public final class ClockDifference {
    /**
     * The difference in milliseconds.
     *
     * Positive value means the agent is behind the master,
     * negative value means the agent is ahead of the master.
     */
    @Exported
    public final long diff;

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
     * Gets the absolute value of {@link #diff}.
     */
    public long abs() {
        return Math.abs(diff);
    }

    /**
     * Gets the clock difference in HTML string.
     */
    @Override
    public String toString() {
        if(-1000<diff && diff <1000)
            return Messages.ClockDifference_InSync();  // clock is in sync

        long abs = Math.abs(diff);

        String s = Util.getTimeSpanString(abs);
        if(diff<0)
            s = Messages.ClockDifference_Ahead(s);
        else
            s = Messages.ClockDifference_Behind(s);

        return s;
    }

    public String toHtml() {
        String s = toString();
        if(isDangerous())
            s = Util.wrapToErrorSpan(s);
        return s;
    }

    public static String toHtml(Node d) {
        try {
            if(d==null) return FAILED_HTML;
            return d.getClockDifference().toHtml();
        } catch (IOException e) {
            return FAILED_HTML;
        } catch (InterruptedException e) {
            return FAILED_HTML;
        }
    }

    /**
     * Gets the clock difference in HTML string.
     * This version handles null {@link ClockDifference}.
     */
    public static String toHtml(ClockDifference d) {
        if(d==null)     return FAILED_HTML;
        return d.toHtml();
    }

    public static final ClockDifference ZERO = new ClockDifference(0);

    private static final String FAILED_HTML =
            "<span class='error'>" + Messages.ClockDifference_Failed() + "</span>";
}
