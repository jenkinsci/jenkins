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
package hudson.bugs.seasar;

import hudson.maven.MavenModuleSet;
import hudson.model.Item;
import hudson.model.FreeStyleProject;
import hudson.tasks.BuildTrigger;

import java.util.Collections;

import org.jvnet.hudson.test.HudsonTestCase;

/**
 * See http://ml.seasar.org/archives/operation/2008-November/004003.html
 *
 * @author Kohsuke Kawaguchi
 */
public class Operation2174Test extends HudsonTestCase {
    /**
     * Tests that configuring a dependency from a freestyle to a maven project actually works.
     */
    public void testBuildChains() throws Exception {
        FreeStyleProject up = createFreeStyleProject("up");
        MavenModuleSet dp = jenkins.createProject(MavenModuleSet.class, "dp");

        // designate 'dp' as the downstream in 'up'
        WebClient webClient = new WebClient();
        webClient.getPage(up,"configure");

        // configure downstream build
        up.getPublishersList().add(new BuildTrigger("dp",false));
        configRoundtrip((Item)up);

        // verify that the relationship is set up
        BuildTrigger trigger = up.getPublishersList().get(BuildTrigger.class);
        assertEquals(trigger.getChildProjects(up), Collections.singletonList(dp));

        // now go ahead and edit the downstream
        configRoundtrip((Item)dp);

        // verify that the relationship is set up
        trigger = up.getPublishersList().get(BuildTrigger.class);
        assertNotNull(trigger);
        assertEquals(trigger.getChildProjects(up), Collections.singletonList(dp));
    }
}
