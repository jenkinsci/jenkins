/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package hudson.maven;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import hudson.model.Descriptor;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.Maven.MavenInstallation;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Bug;
import org.xml.sax.SAXException;

import java.io.IOException;

/**
 * @author huybrechts
 */
public class MavenProjectTest extends HudsonTestCase {

    public void testOnMaster() throws Exception {
        MavenModuleSet project = createSimpleProject();
        project.setGoals("validate");

        assertBuildStatusSuccess(project.scheduleBuild2(0).get());
    }

    private MavenModuleSet createSimpleProject() throws Exception {
        MavenModuleSet project = createMavenProject();
        MavenInstallation mi = configureDefaultMaven();
        project.setScm(new ExtractResourceSCM(getClass().getResource(
                "/simple-projects.zip")));
        project.setMaven(mi.getName());
        return project;
    }

    public void testOnSlave() throws Exception {
        MavenModuleSet project = createSimpleProject();
        project.setGoals("validate");
        project.setAssignedLabel(createSlave().getSelfLabel());

        assertBuildStatusSuccess(project.scheduleBuild2(0).get());
    }

    /**
     * Makes sure that {@link ArtifactArchiver} doesn't show up in the m2 job type config screen.
     * This is to make sure that the exclusion in {@link MavenModuleSet.DescriptorImpl#isApplicable(Descriptor)}
     * is working.
     */
    public void testExclusion() throws Exception {
        MavenModuleSet p = createMavenProject();
        HtmlPage page = new WebClient().getPage(p, "configure");
        assertFalse(page.getWebResponse().getContentAsString().contains(hudson.getDescriptorByType(ArtifactArchiver.DescriptorImpl.class).getDisplayName()));
        // but this should exist. This verifies that the approach of the test is sane (and for example, to make sure getContentAsString()!="")
        assertTrue(page.getWebResponse().getContentAsString().contains(hudson.getDescriptorByType(RedeployPublisher.DescriptorImpl.class).getDisplayName()));
    }

    /**
     * Check if the generated site is linked correctly.
     */
    @Bug(3497)
    public void testSiteBuild() throws Exception {
        MavenModuleSet project = createSimpleProject();
        project.setGoals("site");

        assertBuildStatusSuccess(project.scheduleBuild2(0).get());

        // this should succeed
        HudsonTestCase.WebClient wc = new WebClient();
        wc.getPage(project,"site");
        try {
            wc.getPage(project,"site/no-such-file");
            fail("should have resulted in 404");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(404,e.getStatusCode());
        }
    }
}
