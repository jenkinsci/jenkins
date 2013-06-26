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

import hudson.maven.local_repo.DefaultLocalRepositoryLocator;
import hudson.maven.local_repo.PerJobLocalRepositoryLocator;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Result;
import hudson.tasks.Maven.MavenInstallation;
import hudson.tasks.Shell;

import java.io.File;

import jenkins.model.Jenkins;
import jenkins.mvn.DefaultGlobalSettingsProvider;
import jenkins.mvn.DefaultSettingsProvider;
import jenkins.mvn.FilePathGlobalSettingsProvider;
import jenkins.mvn.FilePathSettingsProvider;
import jenkins.mvn.GlobalMavenConfig;

import org.junit.Assert;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;

import java.net.HttpURLConnection;

/**
 * @author huybrechts
 */
public class MavenProjectTest extends HudsonTestCase {
    

    public void testOnMaster() throws Exception {
        MavenModuleSet project = createSimpleProject();
        project.setGoals("validate");

        buildAndAssertSuccess(project);
    }
    
    @Bug(16499)
    public void testCopyFromExistingMavenProject() throws Exception {
        MavenModuleSet project = createSimpleProject();
        project.setGoals("abcdefg");
        project.save();
        
        MavenModuleSet copy = (MavenModuleSet) Jenkins.getInstance().copy((AbstractProject<?, ?>)project, "copy" + System.currentTimeMillis());
        assertNotNull("Copied project must not be null", copy);
        assertEquals(project.getGoals(), copy.getGoals());
    }

    private MavenModuleSet createSimpleProject() throws Exception {
        return createProject("/simple-projects.zip");
    }

    private MavenModuleSet createProject(final String scmResource) throws Exception {
        MavenModuleSet project = createMavenProject();
        MavenInstallation mi = configureDefaultMaven();
        project.setScm(new ExtractResourceSCM(getClass().getResource(
                scmResource)));
        project.setMaven(mi.getName());
        project.setLocalRepository(new PerJobLocalRepositoryLocator());
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
        wc.assertFails(project.getUrl() + "site/no-such-file", HttpURLConnection.HTTP_NOT_FOUND);
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
        
        //@Bug(7577): check that site generation succeeds also if only a single module is build
        MavenModule coreModule = project.getModule("mmtest:core");
        Assert.assertEquals("site", coreModule.getGoals());
        buildAndAssertSuccess(coreModule);
        wc.getPage(project, "site/core");
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
        project.setLocalRepository(new DefaultLocalRepositoryLocator());
        project.setGoals("install");
        buildAndAssertSuccess(project);
        buildAndAssertSuccess(project.getModule("org.jvnet.hudson.main.test.multimod:moduleB"));
        buildAndAssertSuccess(project);
        assertEquals(2, project.getBuilds().size()); // Module build does not add a ModuleSetBuild
        project.getFirstBuild().delete();
        // A#1, B#1 and B#2 should all be deleted too
        assertEquals(1, project.getModule("org.jvnet.hudson.main.test.multimod:moduleA").getBuilds().size());
        assertEquals(1, project.getModule("org.jvnet.hudson.main.test.multimod:moduleB").getBuilds().size());
    }
    @Bug(7261)
    public void testAbsolutePathPom() throws Exception {
        File pom = new File(this.getClass().getResource("test-pom-7162.xml").toURI());
        MavenModuleSet project = createMavenProject();
        MavenInstallation mi = configureDefaultMaven();
        project.setMaven(mi.getName());
        project.setRootPOM(pom.getAbsolutePath());
        project.setGoals("install");
        buildAndAssertSuccess(project);
    }
    
    @Bug(17177)
    public void testCorrectResultInPostStepAfterFailedPreBuildStep() throws Exception {
        MavenModuleSet p = createSimpleProject();
        MavenInstallation mi = configureDefaultMaven();
        p.setMaven(mi.getName());
        p.setGoals("initialize");
        
        Shell pre = new Shell("exit 1"); // must fail to simulate scenario!
        p.getPrebuilders().add(pre);
        ResultExposingBuilder resultExposer = new ResultExposingBuilder();
        p.getPostbuilders().add(resultExposer);
        
        assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        assertEquals("The result passed to the post build step was not the one from the pre build step", Result.FAILURE, resultExposer.getResult());
    }
    

    /**
     * Config roundtrip test around pre/post build step
     */
    public void testConfigRoundtrip() throws Exception {
        MavenModuleSet m = createMavenProject();
        Shell b1 = new Shell("1");
        Shell b2 = new Shell("2");
        m.getPrebuilders().add(b1);
        m.getPostbuilders().add(b2);
        configRoundtrip((Item)m);

        assertEquals(1,  m.getPrebuilders().size());
        assertNotSame(b1,m.getPrebuilders().get(Shell.class));
        assertEquals("1",m.getPrebuilders().get(Shell.class).getCommand());

        assertEquals(1,  m.getPostbuilders().size());
        assertNotSame(b2,m.getPostbuilders().get(Shell.class));
        assertEquals("2",m.getPostbuilders().get(Shell.class).getCommand());

        for (Result r : new Result[]{Result.SUCCESS, Result.UNSTABLE, Result.FAILURE}) {
            m.setRunPostStepsIfResult(r);
            configRoundtrip((Item)m);
            assertEquals(r,m.getRunPostStepsIfResult());
        }
    }
    
    
    public void testDefaultSettingsProvider() throws Exception {
        {
            MavenModuleSet m = createMavenProject();
    
            assertNotNull(m);
            assertEquals(DefaultSettingsProvider.class, m.getSettings().getClass());
            assertEquals(DefaultGlobalSettingsProvider.class, m.getGlobalSettings().getClass());
        }
        
        {
            GlobalMavenConfig globalMavenConfig = GlobalMavenConfig.get();
            assertNotNull("No global Maven Config available", globalMavenConfig);
            globalMavenConfig.setSettingsProvider(new FilePathSettingsProvider("/tmp/settigns.xml"));
            globalMavenConfig.setGlobalSettingsProvider(new FilePathGlobalSettingsProvider("/tmp/global-settigns.xml"));
            
            MavenModuleSet m = createMavenProject();
            assertEquals(FilePathSettingsProvider.class, m.getSettings().getClass());
            assertEquals("/tmp/settigns.xml", ((FilePathSettingsProvider)m.getSettings()).getPath());
            assertEquals("/tmp/global-settigns.xml", ((FilePathGlobalSettingsProvider)m.getGlobalSettings()).getPath());
        }
    }
}
