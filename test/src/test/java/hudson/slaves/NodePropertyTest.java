package hudson.slaves;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.gargoylesoftware.htmlunit.html.DomNodeUtil;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlLabel;
import hudson.model.Descriptor.FormException;
import hudson.model.Slave;
import net.sf.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Kohsuke Kawaguchi
 */
public class NodePropertyTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void invisibleProperty() throws Exception {
        DumbSlave s = j.createSlave();
        InvisibleProperty before = new InvisibleProperty();
        s.getNodeProperties().add(before);
        assertFalse(before.reconfigured);

        DumbSlave s2 = j.configRoundtrip(s);
        assertNotSame(s,s2);
        InvisibleProperty after = s2.getNodeProperties().get(InvisibleProperty.class);

        assertSame(before,after);
        assertTrue(after.reconfigured);
    }

    public static class InvisibleProperty extends NodeProperty<Slave> {
        boolean reconfigured = false;

        @Override
        public NodeProperty<?> reconfigure(StaplerRequest req, JSONObject form) throws FormException {
            reconfigured = true;
            return this;
        }

        @TestExtension("invisibleProperty")
        public static class DescriptorImpl extends NodePropertyDescriptor {}
    }

    @Test
    public void basicConfigRoundtrip() throws Exception {
        DumbSlave s = j.createSlave();
        HtmlForm f = j.createWebClient().goTo("computer/" + s.getNodeName() + "/configure").getFormByName("config");
        ((HtmlLabel)DomNodeUtil.selectSingleNode(f, ".//LABEL[text()='Some Property']")).click();
        j.submit(f);
        PropertyImpl p = j.jenkins.getNode(s.getNodeName()).getNodeProperties().get(PropertyImpl.class);
        assertEquals("Duke",p.name);

        p.name = "Kohsuke";
        j.configRoundtrip(s);

        PropertyImpl p2 = j.jenkins.getNode(s.getNodeName()).getNodeProperties().get(PropertyImpl.class);
        assertNotSame(p,p2);
        j.assertEqualDataBoundBeans(p, p2);
    }

    public static class PropertyImpl extends NodeProperty<Slave> {
        public String name;

        @DataBoundConstructor
        public PropertyImpl(String name) {
            this.name = name;
        }

        @TestExtension("basicConfigRoundtrip")
        public static class DescriptorImpl extends NodePropertyDescriptor {}
    }
}
