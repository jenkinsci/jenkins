/*
 * The MIT License
 *
 * Copyright (c) 2026, Carrie Chang
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Node;
import hudson.slaves.DumbSlave;
import hudson.util.FormValidation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Characterizes the default behavior with {@code jenkins.model.Nodes.enforceNameRestrictions=true}.
 * With the property set to {@code false}, {@code checkGoodName} is bypassed
 * entirely on the {@code Nodes.addNode}/{@code replaceNode}/{@code addNodeIfAbsent} paths.
 */
@WithJenkins
class CheckGoodNameLengthNodesCharacterizationTest {

    private static final String LONG_NAME = "a".repeat(1000);
    private static final String MODERATELY_LONG_NAME = "a".repeat(100);

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * addNode accepts a moderately long name today.
     */
    @Test
    void addNode_moderatelyLongName() throws Exception {
        DumbSlave slave = new DumbSlave(
                MODERATELY_LONG_NAME, "remoteFS", j.createComputerLauncher(null));

        j.jenkins.addNode(slave);

        Node retrieved = j.jenkins.getNode(MODERATELY_LONG_NAME);
        assertNotNull(retrieved);
        assertEquals(MODERATELY_LONG_NAME, retrieved.getNodeName());
    }

    /**
     * The agent-name form validator approves a clean name longer than common 255-character limits.
     */
    @Test
    void doCheckName_longName() {
        FormValidation fv = j.jenkins.getDescriptorByType(DumbSlave.DescriptorImpl.class)
                .doCheckName(LONG_NAME);

        assertEquals(FormValidation.Kind.OK, fv.kind);
    }

    /**
     * replaceNode re-validates the name on the replace path.
     */
    @Test
    void replaceNode_existingLongNamedNode() throws Exception {
        DumbSlave oldOne = new DumbSlave(
                MODERATELY_LONG_NAME, "remoteFS", j.createComputerLauncher(null));
        DumbSlave newOne = new DumbSlave(
                MODERATELY_LONG_NAME, "remoteFS", j.createComputerLauncher(null));

        j.jenkins.addNode(oldOne);

        assertTrue(j.jenkins.getNodesObject().replaceNode(oldOne, newOne));
        assertNotNull(j.jenkins.getNode(MODERATELY_LONG_NAME));
    }
}
