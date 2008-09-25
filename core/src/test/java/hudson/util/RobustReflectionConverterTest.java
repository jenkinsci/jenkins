package hudson.util;

import junit.framework.TestCase;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.ConversionException;

/**
 * @author Kohsuke Kawaguchi
 */
public class RobustReflectionConverterTest extends TestCase {

    public void testRobustUnmarshalling() {
        Point p = read(new XStream2());
        assertEquals(p.x,1);
        assertEquals(p.y,2);
    }

    private Point read(XStream xs) {
        String clsName = Point.class.getName();
        return (Point) xs.fromXML("<" + clsName + "><x>1</x><y>2</y><z>3</z></" + clsName + '>');
    }

    public void testIfWeNeedWorkaround() {
        try {
            read(new XStream());
            fail();
        } catch (ConversionException e) {
            // expected
            assertTrue(e.getMessage().contains("z"));
        }
    }
}
