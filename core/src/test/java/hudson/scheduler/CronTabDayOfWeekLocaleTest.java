package hudson.scheduler;

import static org.junit.Assert.assertEquals;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.Locale;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Url;

/**
 * A collection of unit tests focused around crontabs restricted to particular
 * days of the week. This flexes across all the locales in the system to check
 * the correctness of the {@link CronTab} class, more specifically the
 * {@link CronTab#floor(Calendar)} and {@link CronTab#ceil(Calendar)} methods.
 */
@For(CronTab.class)
@RunWith(Parameterized.class)
public class CronTabDayOfWeekLocaleTest {
    @Parameters
    public static Collection<Object[]> parameters() {
        final Locale[] locales = Locale.getAvailableLocales();
        final Collection<Object[]> parameters = new ArrayList<Object[]>();
        for (final Locale locale : locales) {
            final Calendar cal = Calendar.getInstance(locale);
            if (GregorianCalendar.class.equals(cal.getClass())) {
                parameters.add(new Object[] { locale });
            }
        }
        return parameters;
    }
    
    private final Locale locale;
    
    public CronTabDayOfWeekLocaleTest(Locale locale) {
        this.locale = locale;
    }
    
    /**
     * This unit test is an slight adaptation of the unit test found in
     * HUDSON-8656.
     *
     * @throws Exception test failure
     */
    @Test
    @Url("http://issues.hudson-ci.org/browse/HUDSON-8656")
    public void hudson8658() throws Exception {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, 0, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 23 * * 1-5"; // execute on weekdays @23:00
        
        final CronTab cron = new CronTab(cronStr);
        final Calendar next = cron.ceil(cal);
        
        final Calendar expectedDate = Calendar.getInstance();
        // Expected next: Monday, Jan 17th 2011, 23:00
        expectedDate.set(2011, 0, 17, 23, 0, 0);
        compare(expectedDate, next);
    }
    
    @Test
    public void isSundayAndNextRunIsMonday() throws Exception {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, 0, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 0 * * 1"; // Mondays @00:00
        
        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.ceil(cal);
        
        final Calendar expected = Calendar.getInstance();
        // Expected next: Monday, Jan 17th 2011, 00:00
        expected.set(2011, 0, 17, 0, 0, 0);
        compare(expected, actual);
    }
    
    @Test
    public void isSundayAndPreviousRunIsMonday() throws Exception {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, 0, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 0 * * 1"; // Mondays @00:00
        
        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.floor(cal);
        
        final Calendar expected = Calendar.getInstance();
        // Expected next: Monday, Jan 10th 2011, 00:00
        expected.set(2011, 0, 10, 0, 0, 0);
        compare(expected, actual);
    }
    
    @Test
    public void isSundayAndNextRunIsTuesday() throws Exception {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, 0, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 0 * * 2"; // Tuesdays @00:00
        
        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.ceil(cal);
        
        final Calendar expected = Calendar.getInstance();
        // Expected next: Tuesday, Jan 18th 2011, 00:00
        expected.set(2011, 0, 18, 0, 0, 0);
        compare(expected, actual);
    }
    
    @Test
    public void isSundayAndPreviousRunIsTuesday() throws Exception {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, 0, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 0 * * 2"; // Tuesdays @00:00
        
        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.floor(cal);
        
        final Calendar expected = Calendar.getInstance();
        // Expected next: Tuesday, Jan 11th 2011, 00:00
        expected.set(2011, 0, 11, 0, 0, 0);
        compare(expected, actual);
    }
    
    @Test
    public void isSundayAndNextRunIsWednesday() throws Exception {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, 0, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 0 * * 3"; // Wednesdays @00:00
        
        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.ceil(cal);
        
        final Calendar expected = Calendar.getInstance();
        // Expected next: Wednesday, Jan 19th 2011, 00:00
        expected.set(2011, 0, 19, 0, 0, 0);
        compare(expected, actual);
    }
    
    @Test
    public void isSundayAndPreviousRunIsWednesday() throws Exception {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, 0, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 0 * * 3"; // Wednesdays @00:00
        
        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.floor(cal);
        
        final Calendar expected = Calendar.getInstance();
        // Expected next: Wednesday, Jan 12th 2011, 00:00
        expected.set(2011, 0, 12, 0, 0, 0);
        compare(expected, actual);
    }
    
    @Test
    public void isSundayAndNextRunIsThursday() throws Exception {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, 0, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 0 * * 4"; // Thursdays @00:00
        
        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.ceil(cal);
        
        final Calendar expected = Calendar.getInstance();
        // Expected next: Thursday, Jan 20th 2011, 00:00
        expected.set(2011, 0, 20, 0, 0, 0);
        compare(expected, actual);
    }
    
    @Test
    public void isSundayAndPreviousRunIsThursday() throws Exception {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, 0, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 0 * * 4"; // Thursdays @00:00
        
        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.floor(cal);
        
        final Calendar expected = Calendar.getInstance();
        // Expected next: Thursday, Jan 13th 2011, 00:00
        expected.set(2011, 0, 13, 0, 0, 0);
        compare(expected, actual);
    }
    
    @Test
    public void isSundayAndNextRunIsFriday() throws Exception {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, 0, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 0 * * 5"; // Fridays @00:00
        
        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.ceil(cal);
        
        final Calendar expected = Calendar.getInstance();
        // Expected next: Friday, Jan 21th 2011, 00:00
        expected.set(2011, 0, 21, 0, 0, 0);
        compare(expected, actual);
    }
    
    @Test
    public void isSundayAndPreviousRunIsFriday() throws Exception {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, 0, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 0 * * 5"; // Fridays @00:00
        
        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.floor(cal);
        
        final Calendar expected = Calendar.getInstance();
        // Expected next: Friday, Jan 14th 2011, 00:00
        expected.set(2011, 0, 14, 0, 0, 0);
        compare(expected, actual);
    }
    
    @Test
    public void isSundayAndNextRunIsSaturday() throws Exception {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, 0, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 0 * * 6"; // Saturdays @00:00
        
        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.ceil(cal);
        
        final Calendar expected = Calendar.getInstance();
        // Expected next: Saturday, Jan 22th 2011, 00:00
        expected.set(2011, 0, 22, 0, 0, 0);
        compare(expected, actual);
    }
    
    @Test
    public void isSundayAndPreviousRunIsSaturday() throws Exception {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, 0, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 0 * * 6"; // Saturdays @00:00
        
        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.floor(cal);
        
        final Calendar expected = Calendar.getInstance();
        // Expected next: Saturday, Jan 15th 2011, 00:00
        expected.set(2011, 0, 15, 0, 0, 0);
        compare(expected, actual);
    }
    
    @Test
    public void isSundayAndNextRunIsNextSunday() throws Exception {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, 0, 16, 1, 0, 0); // Sunday, Jan 16th 2011, 01:00
        final String cronStr = "0 0 * * 0"; // Sundays @00:00
        
        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.ceil(cal);
        
        final Calendar expected = Calendar.getInstance();
        // Expected next: Sunday, Jan 22th 2011, 00:00
        expected.set(2011, 0, 23, 0, 0, 0);
        compare(expected, actual);
    }
    
    @Test
    public void isSundayAndPreviousRunIsPreviousSunday() throws Exception {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, 0, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 1 * * 0"; // Sundays @01:00
        
        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.floor(cal);
        
        final Calendar expected = Calendar.getInstance();
        // Expected next: Sunday, Jan 9th 2011, 01:00
        expected.set(2011, 0, 9, 1, 0, 0);
        compare(expected, actual);
    }

    @Test
    @Issue("JENKINS-12357")
    public void isSundayAndNextRunIsNextSunday7() throws Exception {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, 0, 16, 1, 0, 0); // Sunday, Jan 16th 2011, 01:00
        final String cronStr = "0 0 * * 7"; // Sundays(7 not 0) @00:00

        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.ceil(cal);

        final Calendar expected = Calendar.getInstance();
        // Expected next: Sunday, Jan 22th 2011, 00:00
        expected.set(2011, 0, 23, 0, 0, 0);
        compare(expected, actual);
    }

    @Test
    public void isSundayAndPreviousRunIsPreviousSunday7() throws Exception {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, 0, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 1 * * 7"; // Sundays(7 not 0) @01:00

        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.floor(cal);

        final Calendar expected = Calendar.getInstance();
        // Expected next: Sunday, Jan 9th 2011, 01:00
        expected.set(2011, 0, 9, 1, 0, 0);
        compare(expected, actual);
    }

    @Test
    public void isSaturdayAndNextRunIsSundayAsterisk() throws Exception {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, 0, 15, 1, 0, 0); // Saturday, Jan 15th 2011, 01:00
        final String cronStr = "0 0 * * *"; // Everyday @00:00

        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.ceil(cal);

        final Calendar expected = Calendar.getInstance();
        // Expected next: Sunday, Jan 16th 2011, 00:00
        expected.set(2011, 0, 16, 0, 0, 0);
        compare(expected, actual);
    }

    @Test
    public void isSundayAndPreviousRunIsSaturdayAsterisk() throws Exception {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, 0, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 23 * * *"; // Everyday @23:00

        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.floor(cal);

        final Calendar expected = Calendar.getInstance();
        // Expected next: Saturday, Jan 15th 2011, 23:00
        expected.set(2011, 0, 15, 23, 0, 0);
        compare(expected, actual);
    }

    private void compare(final Calendar expected, final Calendar actual) {
        final DateFormat f = DateFormat.getDateTimeInstance();
        final String msg = "Locale: " + locale + " FirstDayOfWeek: " + actual.getFirstDayOfWeek() + " Expected: "
                + f.format(expected.getTime()) + " Actual: " + f.format(actual.getTime());
        assertEquals(msg, expected.get(Calendar.YEAR), actual.get(Calendar.YEAR));
        assertEquals(msg, expected.get(Calendar.MONTH), actual.get(Calendar.MONTH));
        assertEquals(msg, expected.get(Calendar.DAY_OF_MONTH), actual.get(Calendar.DAY_OF_MONTH));
        assertEquals(msg, expected.get(Calendar.HOUR), actual.get(Calendar.HOUR));
        assertEquals(msg, expected.get(Calendar.MINUTE), actual.get(Calendar.MINUTE));
    }
}
