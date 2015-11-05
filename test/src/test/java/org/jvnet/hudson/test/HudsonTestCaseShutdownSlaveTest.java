/*
 * The MIT License
 * 
 * Copyright (c) 2013 IKEDA Yasuyuki
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
package org.jvnet.hudson.test;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.labels.LabelExpression;
import hudson.slaves.DumbSlave;
import hudson.slaves.SlaveComputer;

/**
 * Test that slaves are cleanly shutdown when test finishes.
 * 
 * In Windows, temporary directories fail to be deleted
 * if log files of slaves are not closed.
 * This causes failures of tests using HudsonTestCase,
 * for an exception occurs in tearDown().
 * 
 * When using JenkinsRule, the exception is squashed in after(),
 * and does not cause failures.
 */
@Issue("JENKINS-18259")
public class HudsonTestCaseShutdownSlaveTest extends HudsonTestCase {
    public void testShutdownSlave() throws Exception {
        DumbSlave slave1 = createOnlineSlave(); // online, and a build finished.
        DumbSlave slave2 = createOnlineSlave(); // online, and a build finished, and disconnected.
        DumbSlave slave3 = createOnlineSlave(); // online, and a build still running.
        DumbSlave slave4 = createOnlineSlave(); // online, and not used.
        DumbSlave slave5 = createSlave();   // offline.
        
        assertNotNull(slave1);
        assertNotNull(slave2);
        assertNotNull(slave3);
        assertNotNull(slave4);
        assertNotNull(slave5);
        
        // A build runs on slave1 and finishes.
        {
            FreeStyleProject project1 = createFreeStyleProject();
            project1.setAssignedLabel(LabelExpression.parseExpression(slave1.getNodeName()));
            project1.getBuildersList().add(new SleepBuilder(1 * 1000));
            assertBuildStatusSuccess(project1.scheduleBuild2(0));
        }
        
        // A build runs on slave2 and finishes, then disconnect slave2 
        {
            FreeStyleProject project2 = createFreeStyleProject();
            project2.setAssignedLabel(LabelExpression.parseExpression(slave2.getNodeName()));
            project2.getBuildersList().add(new SleepBuilder(1 * 1000));
            assertBuildStatusSuccess(project2.scheduleBuild2(0));
            
            SlaveComputer computer2 = slave2.getComputer();
            computer2.disconnect(null);
            computer2.waitUntilOffline();
        }
        
        // A build runs on slave3 and does not finish.
        // This build will be interrupted in tearDown().
        {
            FreeStyleProject project3 = createFreeStyleProject();
            project3.setAssignedLabel(LabelExpression.parseExpression(slave3.getNodeName()));
            project3.getBuildersList().add(new SleepBuilder(10 * 60 * 1000));
            project3.scheduleBuild2(0);
            FreeStyleBuild build;
            while((build = project3.getLastBuild()) == null) {
                Thread.sleep(500);
            }
            assertTrue(build.isBuilding());
        }
    }
}
