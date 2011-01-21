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

import antlr.ANTLRException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import junit.framework.TestCase;
import org.jvnet.hudson.test.Bug;

import static java.util.Calendar.MONDAY;

/**
 * @author Kohsuke Kawaguchi
 */
public class CronTabTest extends TestCase {
    public void test1() throws ANTLRException {
        new CronTab("@yearly");
        new CronTab("@weekly");
        new CronTab("@midnight");
        new CronTab("@monthly");
        new CronTab("0 0 * 1-10/3 *");
    }

    public void testCeil1() throws Exception {
        CronTab x = new CronTab("0,30 * * * *");
        Calendar c = new GregorianCalendar(2000,2,1,1,10);
        compare(new GregorianCalendar(2000,2,1,1,30),x.ceil(c));

        // roll up test
        c =     new GregorianCalendar(2000,2,1,1,40);
        compare(new GregorianCalendar(2000,2,1,2, 0),x.ceil(c));
    }

    public void testCeil2() throws Exception {
        // make sure that lower fields are really reset correctly
        CronTab x = new CronTab("15,45 3 * * *");
        Calendar c = new GregorianCalendar(2000,2,1,2,30);
        compare(new GregorianCalendar(2000,2,1,3,15),x.ceil(c));
    }

    public void testCeil3() throws Exception {
        // conflict between DoM and DoW. In this we need to find a day that's the first day of a month and Sunday
        CronTab x = new CronTab("0 0 1 * 0");
        Calendar c = new GregorianCalendar(2010,0,1,15,55);
        // the first such day in 2010 is Aug 1st
        compare(new GregorianCalendar(2010,7,1,0,0),x.ceil(c));
    }

    public void testFloor1() throws Exception {
        CronTab x = new CronTab("30 * * * *");
        Calendar c = new GregorianCalendar(2000,2,1,1,40);
        compare(new GregorianCalendar(2000,2,1,1,30),x.floor(c));

        // roll down test
        c =     new GregorianCalendar(2000,2,1,1,10);
        compare(new GregorianCalendar(2000,2,1,0,30),x.floor(c));
    }

    public void testFloor2() throws Exception {
        // make sure that lower fields are really reset correctly
        CronTab x = new CronTab("15,45 3 * * *");
        Calendar c = new GregorianCalendar(2000,2,1,4,30);
        compare(new GregorianCalendar(2000,2,1,3,45),x.floor(c));
    }

    public void testFloor3() throws Exception {
        // conflict between DoM and DoW. In this we need to find a day that's the first day of a month and Sunday in 2010
        CronTab x = new CronTab("0 0 1 * 0");
        Calendar c = new GregorianCalendar(2011,0,1,15,55);
        // the last such day in 2010 is Aug 1st
        compare(new GregorianCalendar(2010,7,1,0,0),x.floor(c));
    }

    @Bug(8401)
    public void testFloor4() throws Exception {
        // conflict between DoM and DoW. In this we need to find a day that's the first day of a month and Sunday in 2010
        CronTab x = new CronTab("0 0 1 * 0");
        Calendar c = new GregorianCalendar(2011,0,1,15,55);
        c.setFirstDayOfWeek(MONDAY);
        // the last such day in 2010 is Aug 1st
        GregorianCalendar answer = new GregorianCalendar(2010, 7, 1, 0, 0);
        answer.setFirstDayOfWeek(MONDAY);
        compare(answer,x.floor(c));
    }

    /**
     * Humans can't easily see difference in two {@link Calendar}s, do help the diagnosis by using {@link DateFormat}. 
     */
    private void compare(Calendar a, Calendar b) {
        DateFormat f = DateFormat.getDateTimeInstance();
        System.out.println(f.format(a.getTime())+" vs "+f.format(b.getTime()));
        assertEquals(a,b);
    }

}
