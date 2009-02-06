/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Daniel Dyer, Erik Ramfelt, Richard Bair, id:cactusman
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
package hudson;

import junit.framework.TestCase;

import java.util.Map;
import java.util.HashMap;
import java.util.Locale;

/**
 * @author Kohsuke Kawaguchi
 */
public class UtilTest extends TestCase {
    public void testReplaceMacro() {
        Map<String,String> m = new HashMap<String,String>();
        m.put("A","a");
        m.put("AA","aa");
        m.put("B","B");
        m.put("DOLLAR", "$");
        m.put("ENCLOSED", "a${A}");

        // longest match
        assertEquals("aa",Util.replaceMacro("$AA",m));

        // invalid keys are ignored
        assertEquals("$AAB",Util.replaceMacro("$AAB",m));

        assertEquals("aaB",Util.replaceMacro("${AA}B",m));
        assertEquals("${AAB}",Util.replaceMacro("${AAB}",m));

        // $ escaping
        assertEquals("asd$${AA}dd", Util.replaceMacro("asd$$$${AA}dd",m));

    	// test that more complex scenarios work
        assertEquals("/a/B/aa", Util.replaceMacro("/$A/$B/$AA",m));
        assertEquals("a-aa", Util.replaceMacro("$A-$AA",m));
        assertEquals("/a/foo/can/B/you-believe_aa~it?", Util.replaceMacro("/$A/foo/can/$B/you-believe_$AA~it?",m));
        assertEquals("$$aa$Ba${A}$it", Util.replaceMacro("$$$DOLLAR${AA}$$B${ENCLOSED}$it",m));
    }


    public void testTimeSpanString() {
        // Check that amounts less than 365 days are not rounded up to a whole year.
        // In the previous implementation there were 360 days in a year.
        // We're still working on the assumption that a month is 30 days, so there will
        // be 5 days at the end of the year that will be "12 months" but not "1 year".
        // First check 359 days.
        assertEquals(Messages.Util_month(11), Util.getTimeSpanString(31017600000L));
        // And 362 days.
        assertEquals(Messages.Util_month(12), Util.getTimeSpanString(31276800000L));

        // 11.25 years - Check that if the first unit has 2 or more digits, a second unit isn't used.
        assertEquals(Messages.Util_year(11), Util.getTimeSpanString(354780000000L));
        // 9.25 years - Check that if the first unit has only 1 digit, a second unit is used.
        assertEquals(Messages.Util_year(9)+ " " + Messages.Util_month(3), Util.getTimeSpanString(291708000000L));
        // 67 seconds
        assertEquals(Messages.Util_minute(1) + " " + Messages.Util_second(7), Util.getTimeSpanString(67000L));
        // 17 seconds - Check that times less than a minute only use seconds.
        assertEquals(Messages.Util_second(17), Util.getTimeSpanString(17000L));
        // 1712ms -> 1.7sec
        assertEquals(Messages.Util_second(1.7), Util.getTimeSpanString(1712L));
        // 171ms -> 0.17sec
        assertEquals(Messages.Util_second(0.17), Util.getTimeSpanString(171L));
        // 101ms -> 0.10sec
        assertEquals(Messages.Util_second(0.1), Util.getTimeSpanString(101L));
        // 17ms
        assertEquals(Messages.Util_millisecond(17), Util.getTimeSpanString(17L));
        // 1ms
        assertEquals(Messages.Util_millisecond(1), Util.getTimeSpanString(1L));
        // Test HUDSON-2843 (locale with comma as fraction separator got exception for <10 sec)
        Locale saveLocale = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
        try {
            // Just verifying no exception is thrown:
            assertNotNull("German locale", Util.getTimeSpanString(1234));
            assertNotNull("German locale <1 sec", Util.getTimeSpanString(123));
        }
        finally { Locale.setDefault(saveLocale); }
    }


    /**
     * Test that Strings that contain spaces are correctly URL encoded.
     */
    public void testEncodeSpaces() {
        final String urlWithSpaces = "http://hudson/job/Hudson Job";
        String encoded = Util.encode(urlWithSpaces);
        assertEquals(encoded, "http://hudson/job/Hudson%20Job");
    }
    
    /**
     * Test the rawEncode() method.
     */
    public void testRawEncode() {
        String[] data = {  // Alternating raw,encoded
            "abcdefghijklmnopqrstuvwxyz", "abcdefghijklmnopqrstuvwxyz",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ", "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
            "01234567890!@$&*()-_=+',.", "01234567890!@$&*()-_=+',.",
            " \"#%/:;<>?", "%20%22%23%25%2F%3A%3B%3C%3E%3F",
            "[\\]^`{|}~", "%5B%5C%5D%5E%60%7B%7C%7D%7E",
            "d\u00E9velopp\u00E9s", "d%C3%A9%00velopp%C3%A9%00s",
        };
        for (int i = 0; i < data.length; i += 2) {
            assertEquals("test " + i, data[i + 1], Util.rawEncode(data[i]));
        }
    }

    /**
     * Test the tryParseNumber() method.
     */
    public void testTryParseNumber() {
        assertEquals("Successful parse did not return the parsed value", 20, Util.tryParseNumber("20", 10).intValue());
        assertEquals("Failed parse did not return the default value", 10, Util.tryParseNumber("ss", 10).intValue());
        assertEquals("Parsing empty string did not return the default value", 10, Util.tryParseNumber("", 10).intValue());
        assertEquals("Parsing null string did not return the default value", 10, Util.tryParseNumber(null, 10).intValue());
    }
}
