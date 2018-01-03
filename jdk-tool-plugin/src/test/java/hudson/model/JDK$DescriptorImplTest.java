/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package hudson.model;

import hudson.ExtensionList;
import hudson.model.labels.LabelAtom;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.DumbSlave;
import hudson.tools.ToolLocationNodeProperty;
import java.util.List;
import org.jenkinsci.plugins.jdk_tool.JDKs;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertThat;

public class JDK$DescriptorImplTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void binaryCompatibility() throws Exception {
       /**
        * SSHLauncher.DefaultJavaProvider statically references JDK.DescriptorImpl. This test makes sure that
        * still works after the descriptor has been moved to a new file.
        */
        DumbSlave slave = r.createSlave(new LabelAtom("slave"));
        ToolLocationNodeProperty property = new ToolLocationNodeProperty(
                new ToolLocationNodeProperty.ToolLocation(JDKs.getDescriptor(), "jdk", "foobar"));
        slave.getNodeProperties().add(property);
        SSHLauncher.DefaultJavaProvider provider = ExtensionList.lookupSingleton(SSHLauncher.DefaultJavaProvider.class);
        List<String> javaTools = provider.getJavas(slave.getComputer(), TaskListener.NULL, null);
        assertThat(javaTools, hasItem("foobar/bin/java"));
    }
}
