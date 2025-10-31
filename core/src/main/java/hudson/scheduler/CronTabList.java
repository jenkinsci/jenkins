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
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Calendar;
import java.util.Collection;
import java.util.TimeZone;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * {@link CronTab} list (logically OR-ed).
 *
 * @author Kohsuke Kawaguchi
 */
public final class CronTabList {
    private final Vector<CronTab> tabs;

    public CronTabList(Collection<CronTab> tabs) {
        this.tabs = new Vector<>(tabs);
    }

    /**
     * Returns true if the given calendar matches
     */
    public synchronized boolean check(Calendar cal) {
        for (CronTab tab : tabs) {
            if (tab.check(cal))
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
            if (s != null) return s;
        }
        return null;
    }

    /**
     * Checks if given timezone string is supported by TimeZone and returns
     * the same string if valid, null otherwise
     * @since 1.615
     */
    public static @CheckForNull String getValidTimezone(String timezone) {
        String[] validIDs = TimeZone.getAvailableIDs();
        for (String str : validIDs) {
              if (str != null && str.equals(timezone)) {
                    return timezone;
              }
        }
        return null;
    }

    /**
     * @param format the crontab entry to be parsed
     * @throws IllegalArgumentException if the crontab entry cannot be parsed
     */
    public static CronTabList create(@NonNull String format) {
        return create(format, null);
    }

    /**
     * @param format the crontab entry to be parsed
     * @throws IllegalArgumentException if the crontab entry cannot be parsed
     * @deprecated Throws IllegalArgumentException instead of ANTLRException. Some plugins may not catch the new exception type and could break.
     */
    public static CronTabList create(@NonNull String format, Hash hash) {
        Vector<CronTab> r = new Vector<>();
        int lineNumber = 0;
        String timezone = null;

        for (String line : format.split("\\r?\\n")) {
            lineNumber++;
            line = line.trim();

            if (lineNumber == 1 && line.startsWith("TZ=")) {
                final String timezoneString = line.replace("TZ=", "");
                timezone = getValidTimezone(timezoneString);
                if (timezone != null) {
                    LOGGER.log(Level.CONFIG, "CRON with timezone {0}", timezone);
                } else {
                    throw new ANTLRException("Invalid or unsupported timezone '" + timezoneString + "'");
                }
                continue;
            }

            if (line.isEmpty() || line.startsWith("#"))
                continue;   // ignorable line
            try {
                r.add(new CronTab(line, lineNumber, hash, timezone));
            } catch (IllegalArgumentException e) {
                throw new ANTLRException(Messages.CronTabList_InvalidInput(line, e.getMessage()), e);
            }
        }

        return new CronTabList(r);
    }

    @Restricted(NoExternalUse.class) // just for form validation
    public @CheckForNull Calendar previous() {
        Calendar nearest = null;
        for (CronTab tab : tabs) {
            Calendar scheduled = tab.floor(tab.getTimeZone() == null ? Calendar.getInstance() : Calendar.getInstance(tab.getTimeZone()));
            if (nearest == null || nearest.before(scheduled)) {
                nearest = scheduled;
            }
        }
        return nearest;
    }

    @Restricted(NoExternalUse.class) // just for form validation
    public @CheckForNull Calendar next() {
        Calendar nearest = null;
        for (CronTab tab : tabs) {
            Calendar scheduled = tab.ceil(tab.getTimeZone() == null ? Calendar.getInstance() : Calendar.getInstance(tab.getTimeZone()));
            if (nearest == null || nearest.after(scheduled)) {
                nearest = scheduled;
            }
        }
        return nearest;
    }

    private static final Logger LOGGER = Logger.getLogger(CronTabList.class.getName());
}
