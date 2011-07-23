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
    
    private XStream2 xstream = new XStream2();
    
    public static class Foo {
        ConcurrentHashMap<String,String> m = new ConcurrentHashMap<String,String>();
    }

    /**
     * Tests that ConcurrentHashMap is serialized into a more compact format,
     * but still can deserialize to older, verbose format.
     */
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
            //System.out.println(xml);
            Foo deserialized = (Foo)xstream.fromXML(xml);
            assertEquals(2,deserialized.m.size());
            assertEquals("def", deserialized.m.get("abc"));
            assertEquals("jkl", deserialized.m.get("ghi"));
        } finally {
            v.delete();
        }

        // should be able to read in old data just fine
        Foo map = (Foo)new XStream2().fromXML(getClass().getResourceAsStream("old-concurrentHashMap.xml"));
        assertEquals(1,map.m.size());
        assertEquals("def",map.m.get("abc"));
    }
}
