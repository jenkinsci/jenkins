/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Alan Harder
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
package hudson.tasks;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.Build;
import hudson.model.Project;
import hudson.model.Result;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.MockBuilder;

/**
 * Tests for hudson.tasks.BuildTrigger
 * @author Alan.Harder@sun.com
 */
public class BuildTriggerTest extends HudsonTestCase {
    private void doTriggerTest(boolean evenWhenUnstable, Result triggerResult,
            Result dontTriggerResult) throws Exception {
        Project p = createFreeStyleProject(),
                dp = createFreeStyleProject("downstream");
        p.getPublishersList().add(new BuildTrigger("downstream", evenWhenUnstable));
        p.getBuildersList().add(new MockBuilder(dontTriggerResult));
        hudson.rebuildDependencyGraph();

        // Hm, no setQuietPeriod, have to submit form..
        WebClient webClient = new WebClient();
        HtmlPage page = webClient.getPage(dp,"configure");
        HtmlForm form = page.getFormByName("config");
        form.getInputByName("hasCustomQuietPeriod").click();
        form.getInputByName("quiet_period").setValueAttribute("0");
        submit(form);
        assertEquals("set quiet period", 0, dp.getQuietPeriod());

        // First build should not trigger downstream job
        Build b = (Build)p.scheduleBuild2(0).get();
        for (int i = 0; i < 3; i++) {
            Thread.sleep(200);
            assertTrue("downstream build should not run!  upstream log: " + getLog(b),
                       !dp.isInQueue() && !dp.isBuilding() && dp.getLastBuild()==null);
        }

        // Next build should trigger downstream job
        p.getBuildersList().replace(new MockBuilder(triggerResult));
        b = (Build)p.scheduleBuild2(0).get();
        // Wait for downstream build
        for (int i = 0; dp.getLastBuild()==null && i < 20; i++) Thread.sleep(100);
        assertNotNull("downstream build didn't run.. upstream log: " + getLog(b), dp.getLastBuild());
    }

    public void testBuildTrigger() throws Exception {
        doTriggerTest(false, Result.SUCCESS, Result.UNSTABLE);
    }

    public void testTriggerEvenWhenUnstable() throws Exception {
        doTriggerTest(true, Result.UNSTABLE, Result.FAILURE);
    }
}
