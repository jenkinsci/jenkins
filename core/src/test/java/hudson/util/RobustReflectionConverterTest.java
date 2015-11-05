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

import static org.junit.Assert.*;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.ConversionException;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Test;

/**
 * @author Kohsuke Kawaguchi
 */
public class RobustReflectionConverterTest {

    static {
        Logger.getLogger(RobustReflectionConverter.class.getName()).setLevel(Level.OFF);
    }

    @Test
    public void robustUnmarshalling() {
        Point p = read(new XStream2());
        assertEquals(p.x,1);
        assertEquals(p.y,2);
    }

    private Point read(XStream xs) {
        String clsName = Point.class.getName();
        return (Point) xs.fromXML("<" + clsName + "><x>1</x><y>2</y><z>3</z></" + clsName + '>');
    }

    @Test
    public void ifWorkaroundNeeded() {
        try {
            read(new XStream());
            fail();
        } catch (ConversionException e) {
            // expected
            assertTrue(e.getMessage().contains("z"));
        }
    }

    @Test
    public void classOwnership() throws Exception {
        XStream xs = new XStream2(new XStream2.ClassOwnership() {
            @Override public String ownerOf(Class<?> clazz) {
                Owner o = clazz.getAnnotation(Owner.class);
                return o != null ? o.value() : null;
            }
        });
        String prefix1 = RobustReflectionConverterTest.class.getName() + "_-";
        String prefix2 = RobustReflectionConverterTest.class.getName() + "$";
        Enchufla s1 = new Enchufla();
        s1.number = 1;
        s1.direction = "North";
        Moonwalk s2 = new Moonwalk();
        s2.number = 2;
        s2.boot = new Boot();
        s2.lover = new Billy();
        Moonwalk s3 = new Moonwalk();
        s3.number = 3;
        s3.boot = new Boot();
        s3.jacket = new Jacket();
        s3.lover = new Jean();
        Bild b = new Bild();
        b.steppes = new Steppe[] {s1, s2, s3};
        Projekt p = new Projekt();
        p.bildz = new Bild[] {b};
        assertEquals("<Projekt><bildz><Bild><steppes>"
                + "<Enchufla plugin='p1'><number>1</number><direction>North</direction></Enchufla>"
                // note no plugin='p2' on <boot/> since that would be redundant; <jacket/> is quiet even though unowned
                + "<Moonwalk plugin='p2'><number>2</number><boot/><lover class='Billy' plugin='p3'/></Moonwalk>"
                + "<Moonwalk plugin='p2'><number>3</number><boot/><jacket/><lover class='Jean' plugin='p4'/></Moonwalk>"
                + "</steppes></Bild></bildz></Projekt>",
                xs.toXML(p).replace(prefix1, "").replace(prefix2, "").replaceAll("\r?\n *", "").replace('"', '\''));
        Moonwalk s = (Moonwalk) xs.fromXML("<" + prefix1 + "Moonwalk plugin='p2'><lover class='" + prefix2 + "Billy' plugin='p3'/></" + prefix1 + "Moonwalk>");
        assertEquals(Billy.class, s.lover.getClass());
    }

    @Retention(RetentionPolicy.RUNTIME) @interface Owner {String value();}
    public static class Projekt {
        Bild[] bildz;
    }
    public static class Bild {
        Steppe[] steppes;
    }
    public static abstract class Steppe {
        int number;
    }
    @Owner("p1")
    public static class Enchufla extends Steppe {
        String direction;
    }
    @Owner("p2")
    public static class Moonwalk extends Steppe {
        Boot boot;
        Jacket jacket;
        Lover lover;
    }
    @Owner("p2")
    public static class Boot {}
    public static class Jacket {}
    @Owner("p2")
    public static abstract class Lover {}
    @Owner("p3")
    public static class Billy extends Lover {}
    @Owner("p4")
    public static class Jean extends Lover {}

}
