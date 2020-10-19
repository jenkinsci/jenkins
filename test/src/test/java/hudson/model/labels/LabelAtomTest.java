package hudson.model.labels;

import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.empty;

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
        assertThat(l.getNodes(), containsInAnyOrder(n1,n2));
        assertThat(l2.getNodes().size(), is(2));
        assertThat(l2.getNodes(), containsInAnyOrder(n3,n2));
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

    private static class TestCloud extends Cloud {

        private final List<Label> labels;

        public TestCloud(String name, String labelString) {
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
