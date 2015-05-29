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

import static org.junit.Assert.assertEquals;

import hudson.remoting.Callable;
import jenkins.model.Jenkins;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.model.Computer;
import hudson.model.TopLevelItem;
import hudson.XmlFile;
import hudson.Launcher;
import hudson.FilePath;
import hudson.model.labels.LabelAtom;
import hudson.util.ClockDifference;
import hudson.util.DescribableList;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

/**
 * @author Kohsuke Kawaguchi
 */
public class NodeListTest {

    static class DummyNode extends Node {
        String nodeName = Long.toString(new Random().nextLong());
        public String getNodeName() {
            return nodeName;
        }

        public void setNodeName(String name) {
            throw new UnsupportedOperationException();
        }

        public String getNodeDescription() {
            throw new UnsupportedOperationException();
        }

        public Launcher createLauncher(TaskListener listener) {
            throw new UnsupportedOperationException();
        }

        public int getNumExecutors() {
            throw new UnsupportedOperationException();
        }

        public Mode getMode() {
            throw new UnsupportedOperationException();
        }

        public Computer createComputer() {
            throw new UnsupportedOperationException();
        }

        public Set<LabelAtom> getAssignedLabels() {
            throw new UnsupportedOperationException();
        }

        public String getLabelString() {
            throw new UnsupportedOperationException();
        }

        public void setLabelString(String labelString) throws IOException {
            throw new UnsupportedOperationException();
        }

        public FilePath getWorkspaceFor(TopLevelItem item) {
            throw new UnsupportedOperationException();
        }

        public FilePath getRootPath() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Callable<ClockDifference, IOException> getClockDifferenceCallable() {
            throw new UnsupportedOperationException();
        }

        public NodeDescriptor getDescriptor() {
            throw new UnsupportedOperationException();
        }

		@Override
		public DescribableList<NodeProperty<?>, NodePropertyDescriptor> getNodeProperties() {
            throw new UnsupportedOperationException();
		}
    }

    static class EphemeralNode extends DummyNode implements hudson.slaves.EphemeralNode {
        public Node asNode() {
            return this;
        }
    }

    @Test
    public void serialization() throws Exception {
        NodeList nl = new NodeList(new DummyNode(), new EphemeralNode());

        File tmp = File.createTempFile("test","test");
        try {
            XmlFile x = new XmlFile(Jenkins.XSTREAM, tmp);
            x.write(nl);

            String xml = FileUtils.readFileToString(tmp);
            System.out.println(xml);
            assertEquals(6,xml.split("\n").length);

            NodeList back = (NodeList)x.read();

            assertEquals(1,back.size());
            assertEquals(DummyNode.class,back.get(0).getClass());
        } finally {
            tmp.delete();
        }
    }
}
