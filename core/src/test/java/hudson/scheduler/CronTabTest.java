/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, InfraDNA, Inc.
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

import static java.util.Calendar.MONDAY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import antlr.ANTLRException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

/**
 * @author Kohsuke Kawaguchi
 */
public class CronTabTest {

    @Test
    public void test1() {
        new CronTab("@yearly");
        new CronTab("@weekly");
        new CronTab("@midnight");
        new CronTab("@monthly");
        new CronTab("0 0 * 1-10/3 *");
    }

    @Test
    public void testCeil1() throws Exception {
        CronTab x = new CronTab("0,30 * * * *");
        Calendar c = new GregorianCalendar(2000, Calendar.MARCH, 1, 1, 10);
        compare(new GregorianCalendar(2000, Calendar.MARCH, 1, 1, 30), x.ceil(c));

        // roll up test
        c =     new GregorianCalendar(2000, Calendar.MARCH, 1, 1, 40);
        compare(new GregorianCalendar(2000, Calendar.MARCH, 1, 2, 0), x.ceil(c));
    }

    @Test
    public void testCeil2() throws Exception {
        // make sure that lower fields are really reset correctly
        CronTab x = new CronTab("15,45 3 * * *");
        Calendar c = new GregorianCalendar(2000, Calendar.MARCH, 1, 2, 30);
        compare(new GregorianCalendar(2000, Calendar.MARCH, 1, 3, 15), x.ceil(c));
    }

    @Test
    public void testCeil3() throws Exception {
        // conflict between DoM and DoW. In this we need to find a day that's the first day of a month and Sunday
        CronTab x = new CronTab("0 0 1 * 0");
        Calendar c = new GregorianCalendar(2010, Calendar.JANUARY, 1, 15, 55);
        // the first such day in 2010 is Aug 1st
        compare(new GregorianCalendar(2010, Calendar.AUGUST, 1, 0, 0), x.ceil(c));
    }

    @Test(timeout = 1000)
    @Issue("JENKINS-12357")
    public void testCeil3_DoW7() throws Exception {
        // similar to testCeil3, but DoW=7 may stuck in an infinite loop
        CronTab x = new CronTab("0 0 1 * 7");
        Calendar c = new GregorianCalendar(2010, Calendar.JANUARY, 1, 15, 55);
        // the first such day in 2010 is Aug 1st
        compare(new GregorianCalendar(2010, Calendar.AUGUST, 1, 0, 0), x.ceil(c));
    }

    /**
     * Verifies that HUDSON-8656 never crops up again.
     */
    @Issue("HUDSON-8656") // This is _not_ JENKINS-8656
    @Test
    public void testCeil4() {
        final Calendar cal = Calendar.getInstance(new Locale("de", "de"));
        cal.set(2011, Calendar.JANUARY, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 23 * * 1-5"; // execute on weekdays @23:00

        final CronTab cron = new CronTab(cronStr);
        final Calendar next = cron.ceil(cal);

        final Calendar expectedDate = Calendar.getInstance();
        expectedDate.set(2011, Calendar.JANUARY, 17, 23, 0, 0); // Expected next: Monday, Jan 17th 2011, 23:00
        assertEquals(expectedDate.get(Calendar.HOUR), next.get(Calendar.HOUR));
        assertEquals(expectedDate.get(Calendar.MINUTE), next.get(Calendar.MINUTE));
        assertEquals(expectedDate.get(Calendar.YEAR), next.get(Calendar.YEAR));
        assertEquals(expectedDate.get(Calendar.MONTH), next.get(Calendar.MONTH));
        assertEquals(expectedDate.get(Calendar.DAY_OF_MONTH), next.get(Calendar.DAY_OF_MONTH)); // FAILS: is Monday, Jan 10th, 23:00
    }

    /**
     * Verifies that HUDSON-8656 never crops up again.
     */
    @Issue("HUDSON-8656") // This is _not_ JENKINS-8656
    @Test
    public void testCeil5() {
        final Calendar cal = Calendar.getInstance(new Locale("de", "at"));
        cal.set(2011, Calendar.JANUARY, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 23 * * 1-5"; // execute on weekdays @23:00

        final CronTab cron = new CronTab(cronStr);
        final Calendar next = cron.ceil(cal);

        final Calendar expectedDate = Calendar.getInstance();
        expectedDate.set(2011, Calendar.JANUARY, 17, 23, 0, 0); // Expected next: Monday, Jan 17th 2011, 23:00
        assertEquals(expectedDate.get(Calendar.HOUR), next.get(Calendar.HOUR));
        assertEquals(expectedDate.get(Calendar.MINUTE), next.get(Calendar.MINUTE));
        assertEquals(expectedDate.get(Calendar.YEAR), next.get(Calendar.YEAR));
        assertEquals(expectedDate.get(Calendar.MONTH), next.get(Calendar.MONTH));
        assertEquals(expectedDate.get(Calendar.DAY_OF_MONTH), next.get(Calendar.DAY_OF_MONTH)); // FAILS: is Monday, Jan 10th, 23:00
    }

    @Test
    public void testFloor1() throws Exception {
        CronTab x = new CronTab("30 * * * *");
        Calendar c = new GregorianCalendar(2000, Calendar.MARCH, 1, 1, 40);
        compare(new GregorianCalendar(2000, Calendar.MARCH, 1, 1, 30), x.floor(c));

        // roll down test
        c =     new GregorianCalendar(2000, Calendar.MARCH, 1, 1, 10);
        compare(new GregorianCalendar(2000, Calendar.MARCH, 1, 0, 30), x.floor(c));
    }

    @Test
    public void testFloor2() throws Exception {
        // make sure that lower fields are really reset correctly
        CronTab x = new CronTab("15,45 3 * * *");
        Calendar c = new GregorianCalendar(2000, Calendar.MARCH, 1, 4, 30);
        compare(new GregorianCalendar(2000, Calendar.MARCH, 1, 3, 45), x.floor(c));
    }

    @Test
    public void testFloor3() throws Exception {
        // conflict between DoM and DoW. In this we need to find a day that's the first day of a month and Sunday in 2010
        CronTab x = new CronTab("0 0 1 * 0");
        Calendar c = new GregorianCalendar(2011, Calendar.JANUARY, 1, 15, 55);
        // the last such day in 2010 is Aug 1st
        compare(new GregorianCalendar(2010, Calendar.AUGUST, 1, 0, 0), x.floor(c));
    }

    @Issue("JENKINS-8401")
    @Test
    public void testFloor4() throws Exception {
        // conflict between DoM and DoW. In this we need to find a day that's the first day of a month and Sunday in 2010
        CronTab x = new CronTab("0 0 1 * 0");
        Calendar c = new GregorianCalendar(2011, Calendar.JANUARY, 1, 15, 55);
        c.setFirstDayOfWeek(MONDAY);
        // the last such day in 2010 is Aug 1st
        GregorianCalendar answer = new GregorianCalendar(2010, Calendar.AUGUST, 1, 0, 0);
        answer.setFirstDayOfWeek(MONDAY);
        compare(answer, x.floor(c));
    }

    @Test public void checkSanity() throws Exception {
        assertNull(new CronTab("@hourly").checkSanity());
        assertEquals(Messages.CronTab_do_you_really_mean_every_minute_when_you("* * * * *", "H * * * *"), new CronTab("* * * * *").checkSanity());
        assertEquals(Messages.CronTab_do_you_really_mean_every_minute_when_you("*/1 * * * *", "H * * * *"), new CronTab("*/1 * * * *").checkSanity());
        assertNull(new CronTab("H H(0-2) * * *", Hash.from("stuff")).checkSanity());
        assertEquals(Messages.CronTab_do_you_really_mean_every_minute_when_you("* 0 * * *", "H 0 * * *"), new CronTab("* 0 * * *").checkSanity());
        assertEquals(Messages.CronTab_do_you_really_mean_every_minute_when_you("* 6,18 * * *", "H 6,18 * * *"), new CronTab("* 6,18 * * *").checkSanity());
        // dubious; could be improved:
        assertEquals(Messages.CronTab_do_you_really_mean_every_minute_when_you("* * 3 * *", "H * 3 * *"), new CronTab("* * 3 * *").checkSanity());
        // promote hashes:
        assertEquals(Messages.CronTab_spread_load_evenly_by_using_rather_than_("H/15 * * * *", "*/15 * * * *"), new CronTab("*/15 * * * *").checkSanity());
        assertEquals(Messages.CronTab_spread_load_evenly_by_using_rather_than_("H/15 * * * *", "0,15,30,45 * * * *"), new CronTab("0,15,30,45 * * * *").checkSanity());
        assertEquals(Messages.CronTab_spread_load_evenly_by_using_rather_than_("H * * * *", "0 * * * *"), new CronTab("0 * * * *").checkSanity());
        assertEquals(Messages.CronTab_spread_load_evenly_by_using_rather_than_("H * * * *", "5 * * * *"), new CronTab("5 * * * *").checkSanity());
        // if the user specifically asked for 3:00 AM, probably we should stick to 3:00–3:59
        assertEquals(Messages.CronTab_spread_load_evenly_by_using_rather_than_("H 3 * * *", "0 3 * * *"), new CronTab("0 3 * * *").checkSanity());
        assertEquals(Messages.CronTab_spread_load_evenly_by_using_rather_than_("H 22 * * 6", "00 22 * * 6"), new CronTab("00 22 * * 6").checkSanity());
        assertNull(new CronTab("H/15 * 1 1 *").checkSanity());
        assertNull(new CronTab("0 3 H/15 * *").checkSanity());
        assertEquals(Messages.CronTab_short_cycles_in_the_day_of_month_field_w(), new CronTab("0 3 H/3 * *").checkSanity());
        assertEquals(Messages.CronTab_short_cycles_in_the_day_of_month_field_w(), new CronTab("0 3 */5 * *").checkSanity());
    }

    /**
     * Humans can't easily see difference in two {@link Calendar}s, do help the diagnosis by using {@link DateFormat}.
     */
    private void compare(Calendar expected, Calendar actual) {
        DateFormat f = DateFormat.getDateTimeInstance();
        assertEquals(f.format(expected.getTime()), f.format(actual.getTime()));
    }

    @Test
    public void testHash1() throws Exception {
        CronTab x = new CronTab("H H(5-8) H/3 H(1-10)/4 *", new Hash() {
            @Override
            public int next(int n) {
                return n - 1;
            }
        });

        assertEquals("59;", bitset(x.bits[0]));
        assertEquals("8;", bitset(x.bits[1]));
        assertEquals("3;6;9;12;15;18;21;24;27;", bitset(x.bits[2]));
        assertEquals("4;8;", bitset(x.bits[3]));
    }

    private static String bitset(long bits) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 64; i++) {
            if ((bits & 1L << i) != 0) {
                b.append(i).append(';');
            }
        }
        return b.toString();
    }

    @Test
    public void testHash2() throws Exception {
        CronTab x = new CronTab("H H(5-8) H/3 H(1-10)/4 *", new Hash() {
            @Override
            public int next(int n) {
                return 1;
            }
        });

        assertEquals("1;", bitset(x.bits[0]));
        assertEquals("6;", bitset(x.bits[1]));
        assertEquals("2;5;8;11;14;17;20;23;26;", bitset(x.bits[2]));
        assertEquals("2;6;10;", bitset(x.bits[3]));
    }

    @Test public void hashedMinute() throws Exception {
        long t = new GregorianCalendar(2013, Calendar.MARCH, 21, 16, 21).getTimeInMillis();
        compare(new GregorianCalendar(2013, Calendar.MARCH, 21, 17, 56), new CronTab("H 17 * * *", Hash.from("stuff")).ceil(t));
        compare(new GregorianCalendar(2013, Calendar.MARCH, 21, 16, 56), new CronTab("H * * * *", Hash.from("stuff")).ceil(t));
        compare(new GregorianCalendar(2013, Calendar.MARCH, 21, 16, 56), new CronTab("@hourly", Hash.from("stuff")).ceil(t));
        compare(new GregorianCalendar(2013, Calendar.MARCH, 21, 17, 20), new CronTab("@hourly", Hash.from("junk")).ceil(t));
        compare(new GregorianCalendar(2013, Calendar.MARCH, 22, 13, 56), new CronTab("H H(12-13) * * *", Hash.from("stuff")).ceil(t));
    }

    @Test public void hashSkips() throws Exception {
        compare(new GregorianCalendar(2013, Calendar.MARCH, 21, 16, 26), new CronTab("H/15 * * * *", Hash.from("stuff")).ceil(new GregorianCalendar(2013, Calendar.MARCH, 21, 16, 21)));
        compare(new GregorianCalendar(2013, Calendar.MARCH, 21, 16, 41), new CronTab("H/15 * * * *", Hash.from("stuff")).ceil(new GregorianCalendar(2013, Calendar.MARCH, 21, 16, 31)));
        compare(new GregorianCalendar(2013, Calendar.MARCH, 21, 16, 56), new CronTab("H/15 * * * *", Hash.from("stuff")).ceil(new GregorianCalendar(2013, Calendar.MARCH, 21, 16, 42)));
        compare(new GregorianCalendar(2013, Calendar.MARCH, 21, 17, 11), new CronTab("H/15 * * * *", Hash.from("stuff")).ceil(new GregorianCalendar(2013, Calendar.MARCH, 21, 16, 59)));
        compare(new GregorianCalendar(2013, Calendar.MARCH, 21, 0, 2), new CronTab("H(0-15)/3 * * * *", Hash.from("junk")).ceil(new GregorianCalendar(2013, Calendar.MARCH, 21, 0, 0)));
        compare(new GregorianCalendar(2013, Calendar.MARCH, 21, 0, 2), new CronTab("H(0-3)/4 * * * *", Hash.from("junk")).ceil(new GregorianCalendar(2013, Calendar.MARCH, 21, 0, 0)));
        compare(new GregorianCalendar(2013, Calendar.MARCH, 21, 1, 2), new CronTab("H(0-3)/4 * * * *", Hash.from("junk")).ceil(new GregorianCalendar(2013, Calendar.MARCH, 21, 0, 5)));

        Locale saveLocale = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);

        try {
            ANTLRException e = assertThrows(ANTLRException.class, () ->
                    compare(new GregorianCalendar(2013, Calendar.MARCH, 21, 0, 0), new CronTab("H(0-3)/15 * * * *", Hash.from("junk")).ceil(new GregorianCalendar(2013, Calendar.MARCH, 21, 0, 0))));
            assertThat(e, instanceOf(IllegalArgumentException.class));
            assertEquals("line 1:9: 15 is an invalid value. Must be within 1 and 4", e.getMessage());
        }
        finally {
            Locale.setDefault(saveLocale);
        }
    }


    @Test public void repeatedHash() throws Exception {
        CronTabList tabs = CronTabList.create("H * * * *\nH * * * *", Hash.from("seed"));
        List<Integer> times = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            if (tabs.check(new GregorianCalendar(2013, Calendar.APRIL, 3, 11, i, 0))) {
                times.add(i);
            }
        }
        assertEquals("[35, 56]", times.toString());
    }

    @Test public void rangeBoundsCheckOK() throws Exception {
        new CronTab("H(0-59) H(0-23) H(1-31) H(1-12) H(0-7)");
    }

    @Test public void rangeBoundsCheckFailHour() {
        Locale saveLocale = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);

        try {
            ANTLRException e = assertThrows(ANTLRException.class, () -> new CronTab("H H(12-24) * * *"));
            assertThat(e, instanceOf(IllegalArgumentException.class));
            assertEquals("line 1:10: 24 is an invalid value. Must be within 0 and 23", e.getMessage());
        }
        finally {
            Locale.setDefault(saveLocale);
        }
    }

    @Test public void rangeBoundsCheckFailMinute() {
        Locale saveLocale = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);

        try {
            ANTLRException e = assertThrows(ANTLRException.class, () -> new CronTab("H(33-66) * * * *"));
            assertThat(e, instanceOf(IllegalArgumentException.class));
            assertEquals("line 1:8: 66 is an invalid value. Must be within 0 and 59", e.getMessage());
        }
        finally {
            Locale.setDefault(saveLocale);
        }
    }

    @Issue("JENKINS-9283")
    @Test public void testTimezone() throws Exception {
        CronTabList tabs = CronTabList.create("TZ=Australia/Sydney\nH * * * *\nH * * * *", Hash.from("seed"));
        List<Integer> times = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
            calendar.set(2013, Calendar.APRIL, 3, 11, i, 0);
            if (tabs.check(calendar)) {
                times.add(i);
            }
        }
        assertEquals("[35, 56]", times.toString());
    }

    @Issue("SECURITY-790")
    @Test(timeout = 1000L) public void testLongMonths() throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MONTH, Calendar.JULY);
        new CronTab("0 0 31 7 *").floor(cal); // would infinite loop
    }

    @Issue("SECURITY-1193")
    @Test(timeout = 1000L) public void testCeilLongMonths() throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.MONTH, Calendar.NOVEMBER);
        new CronTab("0 0 31 * *").ceil(cal); // would infinite loop
    }
}
