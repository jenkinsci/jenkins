/*
 * The MIT License
 *
 * Copyright (c) 2004-2010, Sun Microsystems, Inc.
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

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import hudson.tasks.Maven.MavenInstallation;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Bug;

import java.io.IOException;

/**
 * @author huybrechts
 */
public class MavenProjectTest extends HudsonTestCase {

    public void testOnMaster() throws Exception {
        MavenModuleSet project = createSimpleProject();
        project.setGoals("validate");

        buildAndAssertSuccess(project);
    }

    private MavenModuleSet createSimpleProject() throws Exception {
        return createProject("/simple-projects.zip");
    }

    private MavenModuleSet createProject(final String scmResource) throws IOException, Exception {
        MavenModuleSet project = createMavenProject();
        MavenInstallation mi = configureDefaultMaven();
        project.setScm(new ExtractResourceSCM(getClass().getResource(
                scmResource)));
        project.setMaven(mi.getName());
        return project;
    }

    public void testOnSlave() throws Exception {
        MavenModuleSet project = createSimpleProject();
        project.setGoals("validate");
        project.setAssignedLabel(createSlave().getSelfLabel());

        buildAndAssertSuccess(project);
    }

    /**
     * Check if the generated site is linked correctly.
     */
    @Bug(3497)
    public void testSiteBuild() throws Exception {
        MavenModuleSet project = createSimpleProject();
        project.setGoals("site");

        buildAndAssertSuccess(project);

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

    /**
     * Check if the generated site is linked correctly for multi module projects.
     */
    public void testMultiModuleSiteBuild() throws Exception {
        MavenModuleSet project = createProject("maven-multimodule-site.zip");
        project.setGoals("site");

        buildAndAssertSuccess(project);

        // this should succeed
        HudsonTestCase.WebClient wc = new WebClient();
        wc.getPage(project, "site");
        wc.getPage(project, "site/core");
        wc.getPage(project, "site/client");
    }

    /**
     * Check if the the site goal will work when run from a slave.
     */
    @Bug(5943)
    public void testMultiModuleSiteBuildOnSlave() throws Exception {
        MavenModuleSet project = createProject("maven-multimodule-site.zip");
        project.setGoals("site");
        project.setAssignedLabel(createSlave().getSelfLabel());

        buildAndAssertSuccess(project);

        // this should succeed
        HudsonTestCase.WebClient wc = new WebClient();
        wc.getPage(project, "site");
        wc.getPage(project, "site/core");
        wc.getPage(project, "site/client");
    }

    @Bug(6779)
    public void testDeleteSetBuildDeletesModuleBuilds() throws Exception {
        MavenModuleSet project = createProject("maven-multimod.zip");
        project.setGoals("package");
        buildAndAssertSuccess(project);
        buildAndAssertSuccess(project.getModule("org.jvnet.hudson.main.test.multimod:moduleB"));
        buildAndAssertSuccess(project);
        assertEquals(2, project.getBuilds().size()); // Module build does not add a ModuleSetBuild
        project.getFirstBuild().delete();
        // A#1, B#1 and B#2 should all be deleted too
        assertEquals(1, project.getModule("org.jvnet.hudson.main.test.multimod:moduleA").getBuilds().size());
        assertEquals(1, project.getModule("org.jvnet.hudson.main.test.multimod:moduleB").getBuilds().size());
    }
}
