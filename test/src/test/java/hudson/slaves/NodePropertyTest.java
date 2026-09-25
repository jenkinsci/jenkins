package hudson.slaves;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Descriptor;
import hudson.model.Slave;
import java.util.logging.Level;
import net.sf.json.JSONObject;
import org.htmlunit.html.DomNodeUtil;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlLabel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class NodePropertyTest {

    private final LogRecorder logs = new LogRecorder();

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void invisibleProperty() throws Exception {
        logs.record(Descriptor.class, Level.ALL);
        DumbSlave s = j.createSlave();
        InvisibleProperty before = new InvisibleProperty();
        s.getNodeProperties().add(before);
        assertFalse(before.reconfigured);

        DumbSlave s2 = j.configRoundtrip(s);
        assertNotSame(s, s2);
        InvisibleProperty after = s2.getNodeProperties().get(InvisibleProperty.class);

        assertSame(before, after);
        assertTrue(after.reconfigured);
    }

    public static class InvisibleProperty extends NodeProperty<Slave> {
        boolean reconfigured = false;

        @Override
        public NodeProperty<?> reconfigure(StaplerRequest2 req, JSONObject form) {
            reconfigured = true;
            return this;
        }

        @TestExtension("invisibleProperty")
        public static class DescriptorImpl extends NodePropertyDescriptor {}
    }

    @Test
    void basicConfigRoundtrip() throws Exception {
        DumbSlave s = j.createSlave();
        HtmlForm f = j.createWebClient().goTo("computer/" + s.getNodeName() + "/configure").getFormByName("config");
        ((HtmlLabel) DomNodeUtil.selectSingleNode(f, ".//LABEL[text()='PropertyImpl']")).click();
        j.submit(f);
        PropertyImpl p = j.jenkins.getNode(s.getNodeName()).getNodeProperties().get(PropertyImpl.class);
        assertEquals("Duke", p.name);

        p.name = "Kohsuke";
        j.configRoundtrip(s);

        PropertyImpl p2 = j.jenkins.getNode(s.getNodeName()).getNodeProperties().get(PropertyImpl.class);
        assertNotSame(p, p2);
        j.assertEqualDataBoundBeans(p, p2);
    }

    public static class PropertyImpl extends NodeProperty<Slave> {
        public String name;

        @SuppressWarnings("checkstyle:redundantmodifier")
        @DataBoundConstructor
        public PropertyImpl(String name) {
            this.name = name;
        }

        @TestExtension("basicConfigRoundtrip")
        public static class DescriptorImpl extends NodePropertyDescriptor {}
    }
}
