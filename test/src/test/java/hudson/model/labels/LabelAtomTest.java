package hudson.model.labels;

import hudson.model.Label;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

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
    }
}
