/*
 * The MIT License
 *
 * Copyright (c) 2015 Red Hat, Inc.
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

import static org.junit.Assert.*;

import java.io.File;

import jenkins.model.Jenkins;
import hudson.slaves.DumbSlave;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

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
}
