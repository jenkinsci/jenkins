package hudson.model.labels;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class LabelAtomTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void selfLabel() throws Exception {
        j.createSlave("node", "label", null);
        Label self = new LabelAtom("node");
        assertThat(self.isSelfLabel(), is(true));
        Label label = new LabelAtom("label");
        assertThat(label.isSelfLabel(), is(false));
        Label selfJenkins = j.jenkins.getLabel("node");
        assertThat(selfJenkins.isSelfLabel(), is(true));
    }

    @Test
    void getNodes() throws Exception {
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
    void getClouds() {
        Cloud test = new TestCloud("test", "label");
        j.jenkins.clouds.add(test);
        Label l = new LabelAtom("label");
        Label l2 = new LabelAtom("label2");
        assertThat(l.getClouds().size(), is(1));
        assertThat(l.getClouds(), containsInAnyOrder(test));
        assertThat(l2.getClouds(), is(empty()));
    }

    @Test
    void isEmpty() throws Exception {
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
    void changeNodeLabel() throws Exception {
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
    void removeNodeLabel() throws Exception {
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

    @Test
    @Issue("https://github.com/jenkinsci/jenkins/issues/27546")
    void negatedLabelExpressionUpdatesWhenNodeLabelsChange() throws Exception {
        var agent = j.createSlave("node", "win", null);
        var notWin = Label.get("!win");
        assertNotNull(notWin);
        assertThat(notWin.contains(agent), is(false));

        // 1. Removing the excluded label from an agent via Jenkins#updateNode (win -> linux) updates !win so contains(agent) is true.
        agent.setLabelString("linux");
        j.jenkins.updateNode(agent);
        assertThat(notWin.contains(agent), is(true));

        // 2. Adding the excluded label to an agent via Jenkins#updateNode (linux -> linux win) updates !win so contains(agent) is false.
        agent.setLabelString("linux win");
        j.jenkins.updateNode(agent);
        assertThat(notWin.contains(agent), is(false));

        // 3. Adding the excluded label to an agent via the agent's Configure UI form (Nodes#replaceNode) updates !win so contains(updatedAgent) is false.
        agent.setLabelString("linux");
        j.jenkins.updateNode(agent);
        assertThat(notWin.contains(agent), is(true));

        var form = j.createWebClient().getPage(agent, "configure").getFormByName("config");
        form.getInputByName("_.labelString").setValue("linux win");
        j.submit(form);
        var updatedAgent = j.jenkins.getNode(agent.getNodeName());
        assertNotNull(updatedAgent);
        assertThat(notWin.contains(updatedAgent), is(false));
        assertThat(notWin.contains(agent), is(false));
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
