/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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

import org.junit.Assert;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.xml.sax.SAXParseException;

import java.io.FileNotFoundException;
import java.net.ConnectException;

import static org.hamcrest.CoreMatchers.containsString;

public class Digester2Security2147Test {

    private static final String RESOURCE_FILE_NAME = "Digester2Security2147TestData.xml";

    @Issue("SECURITY-2147")
    @Test
    public void testProtection() throws Exception {
        try {
            new Digester2().parse(Digester2Security2147Test.class.getResourceAsStream(RESOURCE_FILE_NAME));
            Assert.fail("expected exception");
        } catch (SAXParseException ex) {
            Assert.assertThat(ex.getMessage(), containsString("DOCTYPE is disallowed"));
        }
    }
    @Issue("SECURITY-2147")
    @Test
    public void testUnsafeBehavior() throws Exception {
        try {
            new Digester2(false).parse(Digester2Security2147Test.class.getResourceAsStream(RESOURCE_FILE_NAME));
            Assert.fail("expected exception");
        } catch (FileNotFoundException|ConnectException ex) {
            // network or file access is bad
        }
    }

    @Issue("SECURITY-2147")
    @Test
    public void testEscapeHatch() throws Exception {
        final String key = Digester2.class.getName() + ".UNSAFE";
        try {
            System.setProperty(key, "true");
            new Digester2().parse(Digester2Security2147Test.class.getResourceAsStream(RESOURCE_FILE_NAME));
            Assert.fail("expected exception");
        } catch (FileNotFoundException|ConnectException ex) {
            // network or file access is bad
        } finally {
            System.clearProperty(key);
        }
    }
}
