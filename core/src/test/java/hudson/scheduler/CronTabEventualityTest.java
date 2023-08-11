package hudson.scheduler;

import static org.junit.Assert.fail;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;

@RunWith(Parameterized.class)
@For({CronTab.class, Hash.class})
public class CronTabEventualityTest {
    @Parameterized.Parameters
    public static Collection<Object[]> parameters() {
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

    private String name;
    private Hash hash;

    public CronTabEventualityTest(String name, Hash hash) {
        this.name = name;
        this.hash = hash;
    }

    @Test
    @Issue("JENKINS-12388")
    public void testYearlyWillBeEventuallyTriggeredWithinOneYear() {
        Calendar start = new GregorianCalendar(2012, Calendar.JANUARY, 11, 22, 33); // Jan 11th 2012 22:33
        Calendar limit = createLimit(start, Calendar.YEAR, 1);
        checkEventuality(start, "@yearly", limit);
    }

    @Test
    @Issue("JENKINS-12388")
    public void testAnnuallyWillBeEventuallyTriggeredWithinOneYear() {
        Calendar start = new GregorianCalendar(2012, Calendar.JANUARY, 11, 22, 33); // Jan 11th 2012 22:33
        Calendar limit = createLimit(start, Calendar.YEAR, 1);
        checkEventuality(start, "@annually", limit);
    }

    @Test
    public void testMonthlyWillBeEventuallyTriggeredWithinOneMonth() {
        Calendar start = new GregorianCalendar(2012, Calendar.JANUARY, 11, 22, 33); // Jan 11th 2012 22:33
        Calendar limit = createLimit(start, Calendar.MONTH, 1);
        checkEventuality(start, "@monthly", limit);
    }

    @Test
    public void testWeeklyWillBeEventuallyTriggeredWithinOneWeek() {
        Calendar start = new GregorianCalendar(2012, Calendar.JANUARY, 11, 22, 33); // Jan 11th 2012 22:33
        Calendar limit = createLimit(start, Calendar.WEEK_OF_YEAR, 1);
        checkEventuality(start, "@weekly", limit);
    }

    @Test
    public void testDailyWillBeEventuallyTriggeredWithinOneDay() {
        Calendar start = new GregorianCalendar(2012, Calendar.JANUARY, 11, 22, 33); // Jan 11th 2012 22:33
        Calendar limit = createLimit(start, Calendar.DAY_OF_MONTH, 1);
        checkEventuality(start, "@daily", limit);
    }

    @Test
    public void testMidnightWillBeEventuallyTriggeredWithinOneDay() {
        Calendar start = new GregorianCalendar(2012, Calendar.JANUARY, 11, 22, 33); // Jan 11th 2012 22:33
        Calendar limit = createLimit(start, Calendar.DAY_OF_MONTH, 1);
        checkEventuality(start, "@midnight", limit);
    }

    @Test
    public void testHourlyWillBeEventuallyTriggeredWithinOneHour() {
        Calendar start = new GregorianCalendar(2012, Calendar.JANUARY, 11, 22, 33); // Jan 11th 2012 22:33
        Calendar limit = createLimit(start, Calendar.HOUR, 1);
        checkEventuality(start, "@hourly", limit);
    }

    @Test
    public void testFirstDayOfMonthWillBeEventuallyTriggeredWithinOneMonth() {
        Calendar start = new GregorianCalendar(2012, Calendar.JANUARY, 11, 22, 33); // Jan 11th 2012 22:33
        Calendar limit = createLimit(start, Calendar.MONTH, 1);
        checkEventuality(start, "H H 1 * *", limit);
    }

    @Test
    public void testFirstSundayOfMonthWillBeEventuallyTriggeredWithinOneMonthAndOneWeek() {
        Calendar start = new GregorianCalendar(2012, Calendar.JANUARY, 11, 22, 33); // Jan 11th 2012 22:33
        Calendar limit = createLimit(start, Calendar.DAY_OF_MONTH, 31 + 7);
        // If both day of month and day of week are specified:
        //     UNIX: triggered when either matches
        //     Jenkins: triggered when both match
        checkEventuality(start, "H H 1-7 * 0", limit);
    }

    private void checkEventuality(Calendar start, String crontabFormat, Calendar limit) {
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
