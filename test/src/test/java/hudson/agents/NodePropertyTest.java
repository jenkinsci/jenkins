package hudson.agents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import hudson.model.Descriptor;
import hudson.model.Agent;
import java.util.logging.Level;
import net.sf.json.JSONObject;
import org.htmlunit.html.DomNodeUtil;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlLabel;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * @author Kohsuke Kawaguchi
 */
public class NodePropertyTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule logs = new LoggerRule();

    @Test
    public void invisibleProperty() throws Exception {
        logs.record(Descriptor.class, Level.ALL);
        DumbAgent s = j.createAgent();
        InvisibleProperty before = new InvisibleProperty();
        s.getNodeProperties().add(before);
        assertFalse(before.reconfigured);

        DumbAgent s2 = j.configRoundtrip(s);
        assertNotSame(s, s2);
        InvisibleProperty after = s2.getNodeProperties().get(InvisibleProperty.class);

        assertSame(before, after);
        assertTrue(after.reconfigured);
    }

    public static class InvisibleProperty extends NodeProperty<Agent> {
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
    public void basicConfigRoundtrip() throws Exception {
        DumbAgent s = j.createAgent();
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

    public static class PropertyImpl extends NodeProperty<Agent> {
        public String name;

        @DataBoundConstructor
        public PropertyImpl(String name) {
            this.name = name;
        }

        @TestExtension("basicConfigRoundtrip")
        public static class DescriptorImpl extends NodePropertyDescriptor {}
    }
}
