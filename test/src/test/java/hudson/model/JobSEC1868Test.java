/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
 * Copyright (c) 2015 Christopher Simons
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

import hudson.slaves.RetentionStrategy;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

/**
 * @author Kohsuke Kawaguchi
 */
//TODO to be merged with JobTest after security release
public class JobSEC1868Test {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Issue("SECURITY-1868")
    @Test public void noXssPossible() throws Exception {
        String desiredNodeName = "agent is a better name2 <script>alert(123)</script>";
        String initialNodeName = "agent is a better name";

        NameChangingNode node = new NameChangingNode(j, initialNodeName);
        j.jenkins.addNode(node);
        
        j.waitOnline(node);

        j.jenkins.setNumExecutors(0);
        
        FreeStyleProject p = j.createFreeStyleProject();
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        node.setVirtualName(desiredNodeName);
        
        JenkinsRule.WebClient wc = j.createWebClient();
        AtomicReference<String> alertContent = new AtomicReference<>("");

        wc.setAlertHandler((page, s1) -> 
                alertContent.set(s1)
        );

        wc.withThrowExceptionOnFailingStatusCode(false);
        wc.getPage(p, "buildTimeTrend");

        assertEquals("", alertContent.get());
    }

    /**
     * This special class was created just to avoid running the test on unix only
     * as the only limitation is the file path, if we change only the name, the XSS is possible also under windows
     */
    static class NameChangingNode extends Slave {
        private String virtualName;
        
        public NameChangingNode(JenkinsRule j, String name) throws Exception {
            super(name, "dummy", j.createTmpDir().getPath(), "1", Node.Mode.NORMAL, "", j.createComputerLauncher(null), RetentionStrategy.NOOP, new ArrayList<>());
        }

        public void setVirtualName(String virtualName) {
            this.virtualName = virtualName;
        }
        
        @Override 
        public String getNodeName() {
            if (virtualName != null) {
                return virtualName;
            } else {
                return super.getNodeName();
            }
        }
    } 
}
