package hudson.scheduler;

import static org.junit.jupiter.api.Assertions.fail;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;

@For({CronTab.class, Hash.class})
class CronTabEventualityTest {

    static Collection<Object[]> parameters() {
        Collection<Object[]> parameters = new ArrayList<>();
        parameters.add(new Object[]{"zero", Hash.zero()});
        parameters.add(new Object[]{"seed1", Hash.from("seed1")});
        parameters.add(new Object[]{"seed2", Hash.from("seed2")});
        return parameters;
    }

    private Calendar createLimit(Calendar start, int field, int amount) {
        Calendar limit = (Calendar) start.clone();
        limit.add(field, amount);
        return limit;
    }

    @ParameterizedTest
    @MethodSource("parameters")
    @Issue("JENKINS-12388")
    void testYearlyWillBeEventuallyTriggeredWithinOneYear(String name, Hash hash) {
        Calendar start = new GregorianCalendar(2012, Calendar.JANUARY, 11, 22, 33); // Jan 11th 2012 22:33
        Calendar limit = createLimit(start, Calendar.YEAR, 1);
        checkEventuality(name, hash, start, "@yearly", limit);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    @Issue("JENKINS-12388")
    void testAnnuallyWillBeEventuallyTriggeredWithinOneYear(String name, Hash hash) {
        Calendar start = new GregorianCalendar(2012, Calendar.JANUARY, 11, 22, 33); // Jan 11th 2012 22:33
        Calendar limit = createLimit(start, Calendar.YEAR, 1);
        checkEventuality(name, hash, start, "@annually", limit);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void testMonthlyWillBeEventuallyTriggeredWithinOneMonth(String name, Hash hash) {
        Calendar start = new GregorianCalendar(2012, Calendar.JANUARY, 11, 22, 33); // Jan 11th 2012 22:33
        Calendar limit = createLimit(start, Calendar.MONTH, 1);
        checkEventuality(name, hash, start, "@monthly", limit);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void testWeeklyWillBeEventuallyTriggeredWithinOneWeek(String name, Hash hash) {
        Calendar start = new GregorianCalendar(2012, Calendar.JANUARY, 11, 22, 33); // Jan 11th 2012 22:33
        Calendar limit = createLimit(start, Calendar.WEEK_OF_YEAR, 1);
        checkEventuality(name, hash, start, "@weekly", limit);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void testDailyWillBeEventuallyTriggeredWithinOneDay(String name, Hash hash) {
        Calendar start = new GregorianCalendar(2012, Calendar.JANUARY, 11, 22, 33); // Jan 11th 2012 22:33
        Calendar limit = createLimit(start, Calendar.DAY_OF_MONTH, 1);
        checkEventuality(name, hash, start, "@daily", limit);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void testMidnightWillBeEventuallyTriggeredWithinOneDay(String name, Hash hash) {
        Calendar start = new GregorianCalendar(2012, Calendar.JANUARY, 11, 22, 33); // Jan 11th 2012 22:33
        Calendar limit = createLimit(start, Calendar.DAY_OF_MONTH, 1);
        checkEventuality(name, hash, start, "@midnight", limit);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void testHourlyWillBeEventuallyTriggeredWithinOneHour(String name, Hash hash) {
        Calendar start = new GregorianCalendar(2012, Calendar.JANUARY, 11, 22, 33); // Jan 11th 2012 22:33
        Calendar limit = createLimit(start, Calendar.HOUR, 1);
        checkEventuality(name, hash, start, "@hourly", limit);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void testFirstDayOfMonthWillBeEventuallyTriggeredWithinOneMonth(String name, Hash hash) {
        Calendar start = new GregorianCalendar(2012, Calendar.JANUARY, 11, 22, 33); // Jan 11th 2012 22:33
        Calendar limit = createLimit(start, Calendar.MONTH, 1);
        checkEventuality(name, hash, start, "H H 1 * *", limit);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void testFirstSundayOfMonthWillBeEventuallyTriggeredWithinOneMonthAndOneWeek(String name, Hash hash) {
        Calendar start = new GregorianCalendar(2012, Calendar.JANUARY, 11, 22, 33); // Jan 11th 2012 22:33
        Calendar limit = createLimit(start, Calendar.DAY_OF_MONTH, 31 + 7);
        // If both day of month and day of week are specified:
        //     UNIX: triggered when either matches
        //     Jenkins: triggered when both match
        checkEventuality(name, hash, start, "H H 1-7 * 0", limit);
    }

    private void checkEventuality(String name, Hash hash, Calendar start, String crontabFormat, Calendar limit) {
        CronTab cron = new CronTab(crontabFormat, hash);
        Calendar next = cron.ceil(start);
        if (next.after(limit)) {
            DateFormat f = DateFormat.getDateTimeInstance();
            String msg = "Name: " + name
                    + " Limit: " + f.format(limit.getTime())
                    + " Next: " + f.format(next.getTime());
            fail(msg);
        }
    }
}
