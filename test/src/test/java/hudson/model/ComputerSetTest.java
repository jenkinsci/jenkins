/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;

import hudson.cli.CLI;
import hudson.slaves.DumbSlave;
import org.junit.Rule;
import org.junit.Test;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

/**
 * @author Kohsuke Kawaguchi
 */
public class ComputerSetTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-2821")
    public void pageRendering() throws Exception {
        WebClient client = j.createWebClient();
        j.createSlave();
        client.goTo("computer");
    }

    /**
     * Tests the basic UI behavior of the node monitoring
     */
    @Test
    public void configuration() throws Exception {
        WebClient client = j.createWebClient();
        HtmlForm form = client.goTo("computer/configure").getFormByName("config");
        j.submit(form);
    }

    @Test
    public void nodeOfflineCli() throws Exception {
        DumbSlave s = j.createSlave();

        CLI cli = new CLI(j.getURL());
        try {
            assertTrue(cli.execute("wait-node-offline","xxx")!=0);
            assertTrue(cli.execute("wait-node-online",s.getNodeName())==0);

            s.toComputer().disconnect().get();

            assertTrue(cli.execute("wait-node-offline",s.getNodeName())==0);
        } finally {
            cli.close();
        }
    }

    @Test
    public void getComputerNames() throws Exception {
        assertThat(ComputerSet.getComputerNames(), is(empty()));
        j.createSlave("aNode", "", null);
        assertThat(ComputerSet.getComputerNames(), contains("aNode"));
        j.createSlave("anAnotherNode", "", null);
        assertThat(ComputerSet.getComputerNames(), containsInAnyOrder("aNode", "anAnotherNode"));
    }
}
