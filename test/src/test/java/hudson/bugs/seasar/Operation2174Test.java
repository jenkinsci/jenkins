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

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.maven.MavenModuleSet;
import hudson.model.FreeStyleProject;
import hudson.tasks.BuildTrigger;
import hudson.tasks.BuildTrigger.DescriptorImpl;
import org.jvnet.hudson.test.HudsonTestCase;

import java.util.Collections;

/**
 * See http://ml.seasar.org/archives/operation/2008-November/004003.html
 *
 * @author Kohsuke Kawaguchi
 */
public class Operation2174Test extends HudsonTestCase {
    /**
     * Upstream/downstream relationship lost.
     */
    public void testBuildChains() throws Exception {
        FreeStyleProject up = createFreeStyleProject("up");
        MavenModuleSet dp = createMavenProject("dp");

        // designate 'dp' as the downstream in 'up'
        WebClient webClient = new WebClient();
        HtmlPage page = webClient.getPage(up,"configure");

        HtmlForm form = page.getFormByName("config");

        // configure downstream build
        DescriptorImpl btd = hudson.getDescriptorByType(DescriptorImpl.class);
        form.getInputByName(btd.getJsonSafeClassName()).click();
        form.getInputByName("buildTrigger.childProjects").setValueAttribute("dp");
        submit(form);

        // verify that the relationship is set up
        BuildTrigger trigger = up.getPublishersList().get(BuildTrigger.class);
        assertEquals(trigger.getChildProjects(), Collections.singletonList(dp));

        // now go ahead and edit the downstream
        page = webClient.getPage(dp,"configure");
        form = page.getFormByName("config");
        submit(form);

        // verify that the relationship is set up
        trigger = up.getPublishersList().get(BuildTrigger.class);
        assertNotNull(trigger);
        assertEquals(trigger.getChildProjects(), Collections.singletonList(dp));
    }
}
