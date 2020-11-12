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
package hudson.slaves;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import jenkins.model.Jenkins;
import hudson.model.Node;
import hudson.XmlFile;

import java.io.File;
import java.nio.charset.Charset;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

/**
 * @author Kohsuke Kawaguchi
 */
public class NodeListTest {

    @Test
    public void serialization() throws Exception {
        // create a normal and an ephemeral class, which should not be serialized
        Node dummyNode = mock(Node.class, withSettings().serializable().defaultAnswer(CALLS_REAL_METHODS));
        when(dummyNode.getNodeName()).thenReturn("node1");
        Node ephemeralNode = mock(Node.class, withSettings().extraInterfaces(EphemeralNode.class).defaultAnswer(CALLS_REAL_METHODS));
        when(ephemeralNode.getNodeName()).thenReturn("node2");
        NodeList nl = new NodeList(dummyNode, ephemeralNode);

        File tmp = File.createTempFile("test","test");
        try {
            XmlFile x = new XmlFile(Jenkins.XSTREAM, tmp);
            x.write(nl);

            String xml = FileUtils.readFileToString(tmp, Charset.defaultCharset());
            // check that at least some content
            assertTrue(xml.split("\n").length > 6);

            NodeList back = (NodeList)x.read();

            // there should only be the 'normal' node
            assertEquals(1,back.size());
            assertEquals(dummyNode.getClass(), back.get(0).getClass());
        } finally {
            if (!tmp.delete()) {
                System.out.println("unable to delete File: " + tmp.getName());
            }
        }
    }
}
