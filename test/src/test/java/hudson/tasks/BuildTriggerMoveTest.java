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
package hudson.tasks;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import jenkins.triggers.ReverseBuildTrigger;
import hudson.model.FreeStyleProject;
import hudson.model.Items;
import hudson.model.Result;
import hudson.model.Project;
import hudson.tasks.BuildTrigger;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;

// Execute the same test against BuildTrigger and ReverseBuildTrigger.
@RunWith(Parameterized.class)
public class BuildTriggerMoveTest {

    @Rule public JenkinsRule j = new JenkinsRule();
    private final Adapter a;

    private interface Adapter {
        void configure(Project<?, ?> p, String value) throws IOException;
        void assertTriggeres(Project<?, ?> p, String expected);
    }

    @Parameters
    public static List<Object[]> params() {
        return Arrays.asList(
                new Object[] {new Adapter() {
                    public void configure(Project<?, ?> p, String value) {
                        p.getPublishersList().add(new BuildTrigger(value, false));
                    }
                    public void assertTriggeres(Project<?, ?> p, String expected) {
                        final BuildTrigger buildTrigger = (BuildTrigger) p.getPublishersList().get(0);
                        assertEquals(expected, buildTrigger.getChildProjectsValue());
                    }
                }},
                new Object[] {new Adapter() {
                    public void configure(Project<?, ?> p, String value) throws IOException {
                        p.addTrigger(new ReverseBuildTrigger(value, Result.SUCCESS));
                    }
                    public void assertTriggeres(Project<?, ?> p, String expected) {
                        assertEquals(expected, p.getTrigger(ReverseBuildTrigger.class).getUpstreamProjects());
                    }
                }}
        );
    }

    public BuildTriggerMoveTest(Adapter a) {
        this.a = a;
    }

    @Test
    public void moveJobsBetweenFolders() throws Exception {
        MockFolder folderA = j.createFolder("folderA");
        MockFolder folderB = j.createFolder("folderB");

        FreeStyleProject intoFolder = j.createFreeStyleProject("intoFolder");
        FreeStyleProject betweenFolders = j.createFreeStyleProject("betweenFolders");
        Items.move(betweenFolders, folderA);
        FreeStyleProject fromFolder = j.createFreeStyleProject("fromFolder");
        Items.move(fromFolder, folderB);

        a.configure(intoFolder, "folderA/betweenFolders");
        a.configure(betweenFolders, "../folderB/fromFolder");
        a.configure(fromFolder, "../intoFolder");

        j.jenkins.rebuildDependencyGraph();

        a.assertTriggeres(intoFolder, "folderA/betweenFolders");
        a.assertTriggeres(betweenFolders, "../folderB/fromFolder");
        a.assertTriggeres(fromFolder, "../intoFolder");

        Items.move(intoFolder, folderA);
        a.assertTriggeres(intoFolder, "betweenFolders");
        a.assertTriggeres(betweenFolders, "../folderB/fromFolder");
        a.assertTriggeres(fromFolder, "../folderA/intoFolder");

        Items.move(betweenFolders, folderB);
        a.assertTriggeres(intoFolder, "../folderB/betweenFolders");
        a.assertTriggeres(betweenFolders, "fromFolder");
        a.assertTriggeres(fromFolder, "../folderA/intoFolder");

        Items.move(fromFolder, j.jenkins);
        a.assertTriggeres(intoFolder, "../folderB/betweenFolders");
        a.assertTriggeres(betweenFolders, "../fromFolder");
        a.assertTriggeres(fromFolder, "folderA/intoFolder");

        Items.move(folderB, folderA);
        a.assertTriggeres(intoFolder, "folderB/betweenFolders");
        a.assertTriggeres(betweenFolders, "../../fromFolder");
        a.assertTriggeres(fromFolder, "folderA/intoFolder");

        Items.move(folderB, j.jenkins);
        a.assertTriggeres(intoFolder, "../folderB/betweenFolders");
        a.assertTriggeres(betweenFolders, "../fromFolder");
        a.assertTriggeres(fromFolder, "folderA/intoFolder");
    }

    @Test
    public void renameFolder() throws Exception {
        MockFolder folder = j.createFolder("folder");
        MockFolder nestedFolder = folder.createProject(MockFolder.class, "nestedFolder");
        FreeStyleProject topLevel = j.createFreeStyleProject("topLevel");
        FreeStyleProject nested = folder.createProject(FreeStyleProject.class, "nested");
        FreeStyleProject deeplyNested = nestedFolder.createProject(FreeStyleProject.class, "deeplyNested");

        a.configure(nested, "/topLevel,nestedFolder/deeplyNested");
        a.configure(topLevel, "/folder/nested,folder/nestedFolder/deeplyNested");
        a.configure(deeplyNested, "/folder/nested,../../topLevel");

        a.assertTriggeres(nested, "/topLevel,nestedFolder/deeplyNested");
        a.assertTriggeres(topLevel, "/folder/nested,folder/nestedFolder/deeplyNested");
        a.assertTriggeres(deeplyNested, "/folder/nested,../../topLevel");

        folder.renameTo("renamed");

        a.assertTriggeres(nested, "/topLevel,nestedFolder/deeplyNested");
        a.assertTriggeres(topLevel, "/renamed/nested,renamed/nestedFolder/deeplyNested");
        a.assertTriggeres(deeplyNested, "/renamed/nested,../../topLevel");

        nestedFolder.renameTo("renamed2");

        a.assertTriggeres(nested, "/topLevel,renamed2/deeplyNested");
        a.assertTriggeres(topLevel, "/renamed/nested,renamed/renamed2/deeplyNested");
        a.assertTriggeres(deeplyNested, "/renamed/nested,../../topLevel");
    }
}
