/*
 * The MIT License
 *
 * Copyright (c) 2015 Red Hat, Inc.; Christopher Simons
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
package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.*;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

import java.io.File;

import jenkins.model.Jenkins;
import hudson.slaves.DumbSlave;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

public class ComputerTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    public void discardLogsAfterDeletion() throws Exception {
        DumbSlave delete = j.createOnlineSlave(Jenkins.getInstance().getLabelAtom("delete"));
        DumbSlave keep = j.createOnlineSlave(Jenkins.getInstance().getLabelAtom("keep"));
        File logFile = delete.toComputer().getLogFile();
        assertTrue(logFile.exists());

        Jenkins.getInstance().removeNode(delete);

        assertFalse("Slave log should be deleted", logFile.exists());
        assertFalse("Slave log directory should be deleted", logFile.getParentFile().exists());

        assertTrue("Slave log should be kept", keep.toComputer().getLogFile().exists());
    }

    /**
     * Verify we can't rename a node over an existing node.
     */
    @Issue("JENKINS-31321")
    @Test
    public void testProhibitRenameOverExistingNode() throws Exception {
        final String NOTE = "Rename node to name of another node should fail.";

        Node nodeA = j.createSlave("nodeA", null, null);
        Node nodeB = j.createSlave("nodeB", null, null);

        WebClient wc = j.createWebClient();
        HtmlForm form = wc.getPage(nodeB, "configure").getFormByName("config");
        form.getInputByName("_.name").setValueAttribute("nodeA");

        try {
            j.submit(form);
            fail(NOTE);
        } catch (FailingHttpStatusCodeException e) {
            assertThat(NOTE, e.getStatusCode(), equalTo(400));
            assertThat(NOTE, e.getResponse().getContentAsString(),
                    containsString("Slave called ‘nodeA’ already exists"));
        }
    }
}
