/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * In its own suite to minimize the chance of mucking about with other tests.
 */
class XStream2EncodingTest {

    private static void useNonUTF8() {
        clearDefaultEncoding();
        System.setProperty("file.encoding", "ISO-8859-1");
        assumeTrue(Charset.defaultCharset().name().equals("ISO-8859-1"));
    }

    private static void clearDefaultEncodingAfter() {
        clearDefaultEncoding();
    }

    private static void clearDefaultEncoding() {
        try {
            Field defaultCharset = Charset.class.getDeclaredField("defaultCharset");
            defaultCharset.setAccessible(true);
            defaultCharset.set(null, null);
        } catch (Exception x) {
            // Per JDK-4163515, this is not supported. It happened to work prior to Java 17, though.
            assumeTrue(false, x.toString());
        }
    }

    @SuppressWarnings("deprecation")
    @Test
    void toXMLUnspecifiedEncoding() throws Exception {
      useNonUTF8();
      try {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        XStream2 xs = new XStream2();
        String msg = "k chybě";
        xs.toXML(new Thing(msg), baos);
        byte[] ambiguousXml = baos.toByteArray();
        Thing t = (Thing) xs.fromXML(new ByteArrayInputStream(ambiguousXml));
        assertThat(t.field, not(msg));
        ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
        baos2.write("<?xml version='1.0' encoding='UTF-8'?>\n".getBytes(StandardCharsets.UTF_8));
        baos2.write(ambiguousXml);
        t = (Thing) xs.fromXML(new ByteArrayInputStream(ambiguousXml));
        assertThat(t.field, not(msg));
      } finally {
        clearDefaultEncodingAfter();
      }
    }

    @Test
    void toXMLUTF8() throws Exception {
      useNonUTF8();
      try {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        XStream2 xs = new XStream2();
        String msg = "k chybě";
        xs.toXMLUTF8(new Thing(msg), baos);
        byte[] unspecifiedData = baos.toByteArray();
        Thing t = (Thing) xs.fromXML(new ByteArrayInputStream(unspecifiedData));
        assertThat(t.field, is(msg));
      } finally {
        clearDefaultEncodingAfter();
      }
    }

    public static class Thing {
        public final String field;

        Thing(String field) {
            this.field = field;
        }
    }

}
