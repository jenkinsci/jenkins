/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.util;

import junit.framework.TestCase;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.ConversionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Kohsuke Kawaguchi
 */
public class RobustReflectionConverterTest extends TestCase {

    static {
        Logger.getLogger(RobustReflectionConverter.class.getName()).setLevel(Level.OFF);
    }

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
