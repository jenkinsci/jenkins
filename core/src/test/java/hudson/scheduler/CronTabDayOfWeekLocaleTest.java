package hudson.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.Locale;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;

/**
 * A collection of unit tests focused around crontabs restricted to particular
 * days of the week. This flexes across all the locales in the system to check
 * the correctness of the {@link CronTab} class, more specifically the
 * {@link CronTab#floor(Calendar)} and {@link CronTab#ceil(Calendar)} methods.
 */
@For(CronTab.class)
class CronTabDayOfWeekLocaleTest {

    static Collection<Object[]> parameters() {
        final Locale[] locales = Locale.getAvailableLocales();
        final Collection<Object[]> parameters = new ArrayList<>();
        for (final Locale locale : locales) {
            final Calendar cal = Calendar.getInstance(locale);
            if (GregorianCalendar.class.equals(cal.getClass())) {
                parameters.add(new Object[] { locale });
            }
        }
        return parameters;
    }

    /**
     * This unit test is an slight adaptation of the unit test found in
     * HUDSON-8656.
     */
    // This is _not_ JENKINS-8656
    @ParameterizedTest
    @MethodSource("parameters")
    @Issue("HUDSON-8656")
    void hudson8656(Locale locale) {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, Calendar.JANUARY, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 23 * * 1-5"; // execute on weekdays @23:00

        final CronTab cron = new CronTab(cronStr);
        final Calendar next = cron.ceil(cal);

        final Calendar expected = Calendar.getInstance();
        // Expected next: Monday, Jan 17th 2011, 23:00
        expected.set(2011, Calendar.JANUARY, 17, 23, 0, 0);
        compare(locale, expected, next);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void isSundayAndNextRunIsMonday(Locale locale) {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, Calendar.JANUARY, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 0 * * 1"; // Mondays @00:00

        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.ceil(cal);

        final Calendar expected = Calendar.getInstance();
        // Expected next: Monday, Jan 17th 2011, 00:00
        expected.set(2011, Calendar.JANUARY, 17, 0, 0, 0);
        compare(locale, expected, actual);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void isSundayAndPreviousRunIsMonday(Locale locale) {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, Calendar.JANUARY, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 0 * * 1"; // Mondays @00:00

        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.floor(cal);

        final Calendar expected = Calendar.getInstance();
        // Expected next: Monday, Jan 10th 2011, 00:00
        expected.set(2011, Calendar.JANUARY, 10, 0, 0, 0);
        compare(locale, expected, actual);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void isSundayAndNextRunIsTuesday(Locale locale) {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, Calendar.JANUARY, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 0 * * 2"; // Tuesdays @00:00

        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.ceil(cal);

        final Calendar expected = Calendar.getInstance();
        // Expected next: Tuesday, Jan 18th 2011, 00:00
        expected.set(2011, Calendar.JANUARY, 18, 0, 0, 0);
        compare(locale, expected, actual);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void isSundayAndPreviousRunIsTuesday(Locale locale) {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, Calendar.JANUARY, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 0 * * 2"; // Tuesdays @00:00

        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.floor(cal);

        final Calendar expected = Calendar.getInstance();
        // Expected next: Tuesday, Jan 11th 2011, 00:00
        expected.set(2011, Calendar.JANUARY, 11, 0, 0, 0);
        compare(locale, expected, actual);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void isSundayAndNextRunIsWednesday(Locale locale) {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, Calendar.JANUARY, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 0 * * 3"; // Wednesdays @00:00

        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.ceil(cal);

        final Calendar expected = Calendar.getInstance();
        // Expected next: Wednesday, Jan 19th 2011, 00:00
        expected.set(2011, Calendar.JANUARY, 19, 0, 0, 0);
        compare(locale, expected, actual);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void isSundayAndPreviousRunIsWednesday(Locale locale) {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, Calendar.JANUARY, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 0 * * 3"; // Wednesdays @00:00

        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.floor(cal);

        final Calendar expected = Calendar.getInstance();
        // Expected next: Wednesday, Jan 12th 2011, 00:00
        expected.set(2011, Calendar.JANUARY, 12, 0, 0, 0);
        compare(locale, expected, actual);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void isSundayAndNextRunIsThursday(Locale locale) {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, Calendar.JANUARY, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 0 * * 4"; // Thursdays @00:00

        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.ceil(cal);

        final Calendar expected = Calendar.getInstance();
        // Expected next: Thursday, Jan 20th 2011, 00:00
        expected.set(2011, Calendar.JANUARY, 20, 0, 0, 0);
        compare(locale, expected, actual);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void isSundayAndPreviousRunIsThursday(Locale locale) {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, Calendar.JANUARY, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 0 * * 4"; // Thursdays @00:00

        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.floor(cal);

        final Calendar expected = Calendar.getInstance();
        // Expected next: Thursday, Jan 13th 2011, 00:00
        expected.set(2011, Calendar.JANUARY, 13, 0, 0, 0);
        compare(locale, expected, actual);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void isSundayAndNextRunIsFriday(Locale locale) {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, Calendar.JANUARY, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 0 * * 5"; // Fridays @00:00

        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.ceil(cal);

        final Calendar expected = Calendar.getInstance();
        // Expected next: Friday, Jan 21th 2011, 00:00
        expected.set(2011, Calendar.JANUARY, 21, 0, 0, 0);
        compare(locale, expected, actual);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void isSundayAndPreviousRunIsFriday(Locale locale) {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, Calendar.JANUARY, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 0 * * 5"; // Fridays @00:00

        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.floor(cal);

        final Calendar expected = Calendar.getInstance();
        // Expected next: Friday, Jan 14th 2011, 00:00
        expected.set(2011, Calendar.JANUARY, 14, 0, 0, 0);
        compare(locale, expected, actual);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void isSundayAndNextRunIsSaturday(Locale locale) {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, Calendar.JANUARY, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 0 * * 6"; // Saturdays @00:00

        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.ceil(cal);

        final Calendar expected = Calendar.getInstance();
        // Expected next: Saturday, Jan 22th 2011, 00:00
        expected.set(2011, Calendar.JANUARY, 22, 0, 0, 0);
        compare(locale, expected, actual);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void isSundayAndPreviousRunIsSaturday(Locale locale) {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, Calendar.JANUARY, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 0 * * 6"; // Saturdays @00:00

        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.floor(cal);

        final Calendar expected = Calendar.getInstance();
        // Expected next: Saturday, Jan 15th 2011, 00:00
        expected.set(2011, Calendar.JANUARY, 15, 0, 0, 0);
        compare(locale, expected, actual);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void isSundayAndNextRunIsNextSunday(Locale locale) {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, Calendar.JANUARY, 16, 1, 0, 0); // Sunday, Jan 16th 2011, 01:00
        final String cronStr = "0 0 * * 0"; // Sundays @00:00

        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.ceil(cal);

        final Calendar expected = Calendar.getInstance();
        // Expected next: Sunday, Jan 22th 2011, 00:00
        expected.set(2011, Calendar.JANUARY, 23, 0, 0, 0);
        compare(locale, expected, actual);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void isSundayAndPreviousRunIsPreviousSunday(Locale locale) {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, Calendar.JANUARY, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 1 * * 0"; // Sundays @01:00

        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.floor(cal);

        final Calendar expected = Calendar.getInstance();
        // Expected next: Sunday, Jan 9th 2011, 01:00
        expected.set(2011, Calendar.JANUARY, 9, 1, 0, 0);
        compare(locale, expected, actual);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    @Issue("JENKINS-12357")
    void isSundayAndNextRunIsNextSunday7(Locale locale) {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, Calendar.JANUARY, 16, 1, 0, 0); // Sunday, Jan 16th 2011, 01:00
        final String cronStr = "0 0 * * 7"; // Sundays(7 not 0) @00:00

        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.ceil(cal);

        final Calendar expected = Calendar.getInstance();
        // Expected next: Sunday, Jan 22th 2011, 00:00
        expected.set(2011, Calendar.JANUARY, 23, 0, 0, 0);
        compare(locale, expected, actual);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void isSundayAndPreviousRunIsPreviousSunday7(Locale locale) {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, Calendar.JANUARY, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 1 * * 7"; // Sundays(7 not 0) @01:00

        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.floor(cal);

        final Calendar expected = Calendar.getInstance();
        // Expected next: Sunday, Jan 9th 2011, 01:00
        expected.set(2011, Calendar.JANUARY, 9, 1, 0, 0);
        compare(locale, expected, actual);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void isSaturdayAndNextRunIsSundayAsterisk(Locale locale) {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, Calendar.JANUARY, 15, 1, 0, 0); // Saturday, Jan 15th 2011, 01:00
        final String cronStr = "0 0 * * *"; // Everyday @00:00

        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.ceil(cal);

        final Calendar expected = Calendar.getInstance();
        // Expected next: Sunday, Jan 16th 2011, 00:00
        expected.set(2011, Calendar.JANUARY, 16, 0, 0, 0);
        compare(locale, expected, actual);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void isSundayAndPreviousRunIsSaturdayAsterisk(Locale locale) {
        final Calendar cal = Calendar.getInstance(locale);
        cal.set(2011, Calendar.JANUARY, 16, 0, 0, 0); // Sunday, Jan 16th 2011, 00:00
        final String cronStr = "0 23 * * *"; // Everyday @23:00

        final CronTab cron = new CronTab(cronStr);
        final Calendar actual = cron.floor(cal);

        final Calendar expected = Calendar.getInstance();
        // Expected next: Saturday, Jan 15th 2011, 23:00
        expected.set(2011, Calendar.JANUARY, 15, 23, 0, 0);
        compare(locale, expected, actual);
    }

    private void compare(final Locale locale, final Calendar expected, final Calendar actual) {
        final DateFormat f = DateFormat.getDateTimeInstance();
        final String msg = "Locale: " + locale + " FirstDayOfWeek: " + actual.getFirstDayOfWeek() + " Expected: "
                + f.format(expected.getTime()) + " Actual: " + f.format(actual.getTime());
        assertEquals(expected.get(java.util.Calendar.YEAR), actual.get(java.util.Calendar.YEAR), msg);
        assertEquals(expected.get(java.util.Calendar.MONTH), actual.get(java.util.Calendar.MONTH), msg);
        assertEquals(expected.get(java.util.Calendar.DAY_OF_MONTH), actual.get(Calendar.DAY_OF_MONTH), msg);
        assertEquals(expected.get(Calendar.HOUR), actual.get(Calendar.HOUR), msg);
        assertEquals(expected.get(Calendar.MINUTE), actual.get(Calendar.MINUTE), msg);
    }
}
