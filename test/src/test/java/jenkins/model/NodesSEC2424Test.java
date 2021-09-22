/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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

package jenkins.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import hudson.model.Failure;
import hudson.model.Messages;
import hudson.slaves.DumbSlave;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

//TODO merge back to NodesTest after security release
public class NodesSEC2424Test {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    @Issue("SECURITY-2424")
    public void cannotCreateNodeWithTrailingDot_withoutOtherNode() throws Exception {
        assertThat(r.jenkins.getNodes(), hasSize(0));

        DumbSlave node = new DumbSlave("nodeA.", "temp", r.createComputerLauncher(null));
        try {
            r.jenkins.addNode(node);
            fail("Adding the node should have thrown an exception during checkGoodName");
        } catch (Failure e) {
            assertEquals(Messages.Hudson_TrailingDot(), e.getMessage());
        }

        assertThat(r.jenkins.getNodes(), hasSize(0));
    }

    @Test
    @Issue("SECURITY-2424")
    public void cannotCreateNodeWithTrailingDot_withExistingNode() throws Exception {
        assertThat(r.jenkins.getNodes(), hasSize(0));
        r.createSlave("nodeA", "", null);
        assertThat(r.jenkins.getNodes(), hasSize(1));

        DumbSlave node = new DumbSlave("nodeA.", "temp", r.createComputerLauncher(null));
        try {
            r.jenkins.addNode(node);
            fail("Adding the node should have thrown an exception during checkGoodName");
        } catch (Failure e) {
            assertEquals(Messages.Hudson_TrailingDot(), e.getMessage());
        }

        assertThat(r.jenkins.getNodes(), hasSize(1));
    }

    @Test
    @Issue("SECURITY-2424")
    public void cannotCreateNodeWithTrailingDot_exceptIfEscapeHatchIsSet() throws Exception {
        String propName = Jenkins.NAME_VALIDATION_REJECTS_TRAILING_DOT_PROP;
        String initialValue = System.getProperty(propName);
        System.setProperty(propName, "false");
        try {
            assertThat(r.jenkins.getNodes(), hasSize(0));

            DumbSlave node = new DumbSlave("nodeA.", "temp", r.createComputerLauncher(null));
            r.jenkins.addNode(node);

            assertThat(r.jenkins.getNodes(), hasSize(1));
        } finally {
            if (initialValue == null) {
                System.clearProperty(propName);
            } else {
                System.setProperty(propName, initialValue);
            }
        }
    }
}
