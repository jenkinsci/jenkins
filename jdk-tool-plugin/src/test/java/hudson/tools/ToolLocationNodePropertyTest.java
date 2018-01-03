/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Tom Huybrechts
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.tools;

import static org.junit.Assert.assertEquals;

import hudson.model.*;
import hudson.model.labels.LabelAtom;
import hudson.slaves.DumbSlave;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.jenkinsci.plugins.jdk_tool.JDKs;
import org.junit.rules.TemporaryFolder;

/**
 * This class tests that environment variables from node properties are applied,
 * and that the priority is maintained: parameters > slave node properties >
 * master node properties
 */
public class ToolLocationNodePropertyTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private DumbSlave slave;
    private FreeStyleProject project;

    @Test
    public void formRoundTrip() throws Exception {
        ToolDescriptor<JDK> jdkDescriptor = JDKs.getDescriptor();
        jdkDescriptor.setInstallations(new JDK("jdk", "XXX"));

        ToolLocationNodeProperty property = new ToolLocationNodeProperty(
                new ToolLocationNodeProperty.ToolLocation(jdkDescriptor, "jdk", "foobar"));
        slave.getNodeProperties().add(property);

        WebClient webClient = j.createWebClient();
        HtmlPage page = webClient.getPage(slave, "configure");
        HtmlForm form = page.getFormByName("config");
        j.submit(form);

        assertEquals(1, slave.getNodeProperties().toList().size());

        ToolLocationNodeProperty prop = slave.getNodeProperties().get(ToolLocationNodeProperty.class);
        assertEquals(1, prop.getLocations().size());

        ToolLocationNodeProperty.ToolLocation location = prop.getLocations().get(0);
        assertEquals(jdkDescriptor, location.getType());
        assertEquals("jdk", location.getName());
        assertEquals("foobar", location.getHome());
    }

    @Before
    public void setUp() throws Exception {
        slave = j.createSlave(new LabelAtom("slave"));
        project = j.createFreeStyleProject();
        project.setAssignedLabel(slave.getSelfLabel());
    }
}
