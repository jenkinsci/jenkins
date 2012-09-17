package hudson.slaves;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlLabel;
import hudson.model.Descriptor.FormException;
import hudson.model.Slave;
import net.sf.json.JSONObject;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Kohsuke Kawaguchi
 */
public class NodePropertyTest extends HudsonTestCase {
    public void testInvisibleProperty() throws Exception {
        DumbSlave s = createSlave();
        InvisibleProperty before = new InvisibleProperty();
        s.getNodeProperties().add(before);
        assertFalse(before.reconfigured);


        DumbSlave s2 = configRoundtrip(s);
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

        @TestExtension
        public static class DescriptorImpl extends NodePropertyDescriptor {
            @Override
            public String getDisplayName() {
                return null;
            }
        }
    }

    public void testBasicConfigRoundtrip() throws Exception {
        DumbSlave s = createSlave();
        HtmlForm f = createWebClient().goTo("/computer/" + s.getNodeName() + "/configure").getFormByName("config");
        ((HtmlLabel)f.selectSingleNode(".//LABEL[text()='Some Property']")).click();
        submit(f);
        PropertyImpl p = jenkins.getNode(s.getNodeName()).getNodeProperties().get(PropertyImpl.class);
        assertEquals("Duke",p.name);

        p.name = "Kohsuke";
        configRoundtrip(s);

        PropertyImpl p2 = jenkins.getNode(s.getNodeName()).getNodeProperties().get(PropertyImpl.class);
        assertNotSame(p,p2);
        assertEqualDataBoundBeans(p,p2);
    }

    public static class PropertyImpl extends NodeProperty<Slave> {
        public String name;

        @DataBoundConstructor
        public PropertyImpl(String name) {
            this.name = name;
        }

        @TestExtension("testBasicConfigRoundtrip")
        public static class DescriptorImpl extends NodePropertyDescriptor {
            @Override
            public String getDisplayName() {
                return "Some Property";
            }
        }
    }
}
