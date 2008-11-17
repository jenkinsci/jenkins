package hudson;

import junit.framework.TestCase;

import java.util.Map;
import java.util.HashMap;

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
     * Test the tryParseNumber() method.
     */
    public void testTryParseNumber() {
        assertEquals("Successful parse did not return the parsed value", 20, Util.tryParseNumber("20", 10).intValue());
        assertEquals("Failed parse did not return the default value", 10, Util.tryParseNumber("ss", 10).intValue());
        assertEquals("Parsing empty string did not return the default value", 10, Util.tryParseNumber("", 10).intValue());
        assertEquals("Parsing null string did not return the default value", 10, Util.tryParseNumber(null, 10).intValue());
    }
}
