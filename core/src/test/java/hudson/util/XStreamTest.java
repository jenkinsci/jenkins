/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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

import hudson.XmlFile;
import junit.framework.TestCase;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Kohsuke Kawaguchi
 */
public class XStreamTest extends TestCase {
    public static class Foo {
        ConcurrentHashMap m = new ConcurrentHashMap();
    }

    public void testConcurrentHashMapSerialization() throws Exception {
        Foo foo = new Foo();
        foo.m.put("abc","def");
        foo.m.put("ghi","jkl");
        File v = File.createTempFile("hashmap", "xml");
        try {
            new XmlFile(v).write(foo);

            // should serialize like map
            String xml = FileUtils.readFileToString(v);
            assertFalse(xml.contains("java.util.concurrent"));
            System.out.println(xml);
        } finally {
            v.delete();
        }

        // should be able to read in old data just fine
        Foo map = (Foo)new XStream2().fromXML(getClass().getResourceAsStream("old-concurrentHashMap.xml"));
        assertEquals("def",map.m.get("abc"));
    }
}
