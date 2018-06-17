package jenkins.model;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class IdStrategyTest {
    private IdStrategy idStrategy;

    @Test
    public void caseInsensitive() {
        idStrategy = new IdStrategy.CaseInsensitive();
        assertRestrictedNames();

        assertRoundTrip("foo", "foo");
        assertRoundTrip("foo/bar", "foo$002fbar");
        assertRoundTrip("../test", "..$002ftest");
        assertRoundTrip("0123 _-@~a", "0123 _-@$007ea");
        assertRoundTrip("foo.", "foo$002e");
        assertRoundTrip("-foo", "$002dfoo");

        // Should not return the same username due to case insensitivity
        assertCaseInsensitiveRoundTrip("Foo", "foo");
        assertCaseInsensitiveRoundTrip("Foo/Bar", "foo$002fbar");
        assertCaseInsensitiveRoundTrip("../Test", "..$002ftest");
        assertCaseInsensitiveRoundTrip("NUL", "$006eul");
        assertEquals("foo", idStrategy.idFromFilename("~foo"));
        assertEquals("0123 _-@a", idStrategy.idFromFilename("0123 _-@~a"));
        assertEquals("big$money", idStrategy.idFromFilename("big$money"));
    }

    @Test
    public void caseSensitive() {
        idStrategy = new IdStrategy.CaseSensitive();
        assertRestrictedNames();

        assertRoundTrip("foo", "foo");
        assertRoundTrip("Foo", "~foo");
        assertRoundTrip("foo/bar", "foo$002fbar");
        assertRoundTrip("Foo/Bar", "~foo$002f~bar");
        assertRoundTrip("../test", "..$002ftest");
        assertRoundTrip("../Test", "..$002f~test");
        assertRoundTrip("0123 _-@~a", "0123 _-@$007ea");
        assertRoundTrip("0123 _-@A", "0123 _-@~a");
        assertRoundTrip("foo.", "foo$002e");
        assertRoundTrip("-foo", "$002dfoo");
        assertRoundTrip("Con", "~con");
        assertRoundTrip("Prn", "~prn");
        assertRoundTrip("Aux", "~aux");
        assertRoundTrip("Nul", "~nul");
        assertRoundTrip("Com1", "~com1");
        assertRoundTrip("Lpt1", "~lpt1");
    }

    private void assertRestrictedNames() {
        assertEquals("$002f", idStrategy.filenameOf("."));
        // "." and "/" are equivalent from an implementation standpoint, but both should return "/"
        assertEquals("/", idStrategy.idFromFilename(idStrategy.filenameOf(".")));

        assertEquals("$002f", idStrategy.filenameOf(""));
        // "" and "/" are equivalent from an implementation standpoint, but both should return "/"
        assertEquals("/", idStrategy.idFromFilename(idStrategy.filenameOf("")));

        assertRoundTrip("/", "$002f");
        assertRoundTrip("..", "$002e$002e");
        assertRoundTrip("con", "$0063on");
        assertRoundTrip("prn", "$0070rn");
        assertRoundTrip("aux", "$0061ux");
        assertRoundTrip("nul", "$006eul");
        assertRoundTrip("com1", "$0063om1");
        assertRoundTrip("com2", "$0063om2");
        assertRoundTrip("com3", "$0063om3");
        assertRoundTrip("com4", "$0063om4");
        assertRoundTrip("com5", "$0063om5");
        assertRoundTrip("com6", "$0063om6");
        assertRoundTrip("com7", "$0063om7");
        assertRoundTrip("com8", "$0063om8");
        assertRoundTrip("com9", "$0063om9");
        assertRoundTrip("lpt1", "$006cpt1");
        assertRoundTrip("lpt2", "$006cpt2");
        assertRoundTrip("lpt3", "$006cpt3");
        assertRoundTrip("lpt4", "$006cpt4");
        assertRoundTrip("lpt5", "$006cpt5");
        assertRoundTrip("lpt6", "$006cpt6");
        assertRoundTrip("lpt7", "$006cpt7");
        assertRoundTrip("lpt8", "$006cpt8");
        assertRoundTrip("lpt9", "$006cpt9");
    }

    private void assertRoundTrip(String username, String filename) {
        assertEquals(filename, idStrategy.filenameOf(username));
        assertEquals(username, idStrategy.idFromFilename(filename));
    }

    private void assertCaseInsensitiveRoundTrip(String username, String filename) {
        assertEquals(filename, idStrategy.filenameOf(username));
        assertEquals(username.toLowerCase(Locale.ENGLISH), idStrategy.idFromFilename(filename));
    }
}
