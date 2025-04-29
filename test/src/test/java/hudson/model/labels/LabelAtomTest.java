package hudson.model.labels;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;

import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class LabelAtomTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void selfLabel() throws Exception {
        j.createSlave("node", "label", null);
        Label self = new LabelAtom("node");
        assertThat(self.isSelfLabel(), is(true));
        Label label = new LabelAtom("label");
        assertThat(label.isSelfLabel(), is(false));
        Label selfJenkins = j.jenkins.getLabel("node");
        assertThat(selfJenkins.isSelfLabel(), is(true));
    }

    @Test
    public void getNodes() throws Exception {
        Node n1 = j.createSlave("n1", "label", null);
        Node n2 = j.createSlave("n2", "label label2", null);
        Node n3 = j.createSlave("n3", "label2", null);
        Label l = j.jenkins.getLabel("label");
        Label l2 = j.jenkins.getLabel("label2");
        Label l3 = j.jenkins.getLabel("label3");
        assertThat(l.getNodes().size(), is(2));
        assertThat(l.getNodes(), containsInAnyOrder(n1, n2));
        assertThat(l2.getNodes().size(), is(2));
        assertThat(l2.getNodes(), containsInAnyOrder(n3, n2));
        assertThat(l3.getNodes(), is(empty()));
    }

    @Test
    public void getClouds() {
        Cloud test = new TestCloud("test", "label");
        j.jenkins.clouds.add(test);
        Label l = new LabelAtom("label");
        Label l2 = new LabelAtom("label2");
        assertThat(l.getClouds().size(), is(1));
        assertThat(l.getClouds(), containsInAnyOrder(test));
        assertThat(l2.getClouds(), is(empty()));
    }

    @Test
    public void isEmpty() throws Exception {
        Label l = new LabelAtom("label");
        assertThat(l.isEmpty(), is(true));
        l = new LabelAtom("label");
        j.createSlave("node", "label", null);
        assertThat(l.isEmpty(), is(false));
        Label l2 = new LabelAtom("label2");
        Cloud test = new TestCloud("test", "label2");
        j.jenkins.clouds.add(test);
        assertThat(l2.isEmpty(), is(false));
    }

    @Test
    @Issue("JENKINS-68155")
    public void changeNodeLabel() throws Exception {
        var slave = j.createSlave("node", "label linux", null);
        var label = Label.get("label");
        assertNotNull(label);
        assertThat(label.getNodes(), contains(slave));
        var osLabel = Label.get("linux");
        assertNotNull(osLabel);
        assertThat(osLabel.getNodes(), contains(slave));
        slave.setLabelString("label2 linux");
        j.jenkins.updateNode(slave);
        label = Label.get("label");
        assertNotNull(label);
        assertThat(label.getNodes(), empty());
        var label2 = Label.get("label2");
        assertNotNull(label2);
        assertThat(label2.getNodes(), contains(slave));
        osLabel = Label.get("linux");
        assertNotNull(osLabel);
        assertThat(osLabel.getNodes(), contains(slave));
    }

    @Test
    @Issue("JENKINS-68155")
    public void removeNodeLabel() throws Exception {
        var slave = j.createSlave("node", "label", null);
        var label = Label.get("label");
        assertNotNull(label);
        assertThat(label.getNodes(), contains(slave));
        slave.setLabelString(null);
        j.jenkins.updateNode(slave);
        label = Label.get("label");
        assertNotNull(label);
        assertThat(label.getNodes(), empty());
    }

    private static class TestCloud extends Cloud {

        private final List<Label> labels;

        TestCloud(String name, String labelString) {
            super(name);
            labels = new ArrayList<>();
            for (String l : labelString.split(" ")) {
                labels.add(new LabelAtom(l));
            }
        }

        @Override
        public boolean canProvision(CloudState s) {
            Label stateLabel = s.getLabel();
            for (Label l : labels) {
                if (stateLabel.equals(l)) {
                    return true;
                }
            }
            return false;
        }
    }
}
