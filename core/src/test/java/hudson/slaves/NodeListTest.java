package hudson.slaves;

import junit.framework.TestCase;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.TopLevelItem;
import hudson.XmlFile;
import hudson.Launcher;
import hudson.FilePath;
import hudson.security.ACL;
import hudson.security.Permission;
import hudson.util.ClockDifference;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.apache.commons.io.FileUtils;

/**
 * @author Kohsuke Kawaguchi
 */
public class NodeListTest extends TestCase {
    static class DummyNode implements Node {
        public String getNodeName() {
            throw new UnsupportedOperationException();
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

        public Computer toComputer() {
            throw new UnsupportedOperationException();
        }

        public Computer createComputer() {
            throw new UnsupportedOperationException();
        }

        public Set<Label> getAssignedLabels() {
            throw new UnsupportedOperationException();
        }

        public Set<Label> getDynamicLabels() {
            throw new UnsupportedOperationException();
        }

        public Label getSelfLabel() {
            throw new UnsupportedOperationException();
        }

        public FilePath getWorkspaceFor(TopLevelItem item) {
            throw new UnsupportedOperationException();
        }

        public FilePath getRootPath() {
            throw new UnsupportedOperationException();
        }

        public FilePath createPath(String absolutePath) {
            throw new UnsupportedOperationException();
        }

        public ClockDifference getClockDifference() throws IOException, InterruptedException {
            throw new UnsupportedOperationException();
        }

        public NodeDescriptor getDescriptor() {
            throw new UnsupportedOperationException();
        }

        public ACL getACL() {
            throw new UnsupportedOperationException();
        }

        public void checkPermission(Permission permission) {
            throw new UnsupportedOperationException();
        }

        public boolean hasPermission(Permission permission) {
            throw new UnsupportedOperationException();
        }
    }
    static class EphemeralNode extends DummyNode implements hudson.slaves.EphemeralNode {
        public Cloud getCloud() {
            throw new UnsupportedOperationException();
        }
    }

    public void testSerialization() throws Exception {
        NodeList nl = new NodeList();
        nl.add(new DummyNode());
        nl.add(new EphemeralNode());

        File tmp = File.createTempFile("test","test");
        try {
            XmlFile x = new XmlFile(Hudson.XSTREAM, tmp);
            x.write(nl);

            String xml = FileUtils.readFileToString(tmp);
            System.out.println(xml);
            assertEquals(4,xml.split("\n").length);

            NodeList back = (NodeList)x.read();

            assertEquals(1,back.size());
            assertEquals(DummyNode.class,back.get(0).getClass());
        } finally {
            tmp.delete();
        }
    }
}
