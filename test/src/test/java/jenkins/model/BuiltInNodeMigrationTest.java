/*
 * The MIT License
 *
 * Copyright (c) 2021 Daniel Beck
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

package jenkins.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.ExtensionList;
import hudson.model.FreeStyleProject;
import hudson.model.labels.LabelAtom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

@WithJenkins
class BuiltInNodeMigrationTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void newInstanceHasNewTerminology() throws Exception {
        assertStatus(j, true, false, "built-in", "built-in");
        assertFalse(ExtensionList.lookupSingleton(BuiltInNodeMigration.class).isActivated());
    }

    @Test
    @LocalData
    void oldDataStartsWithOldTerminology() throws Exception {
        assertStatus(j, false, true, "master", "master");
        final BuiltInNodeMigration builtInNodeMigration = ExtensionList.lookupSingleton(BuiltInNodeMigration.class);
        assertTrue(builtInNodeMigration.isActivated());

        // Now perform rename and confirm it's done
        j.jenkins.performRenameMigration();
        assertStatus(j, true, false, "built-in", "built-in");
        assertFalse(builtInNodeMigration.isActivated());
    }

    @Test
    @LocalData
    void migratedInstanceStartsWithNewTerminology() throws Exception {
        assertStatus(j, true, false, "built-in", "built-in");
        assertFalse(ExtensionList.lookupSingleton(BuiltInNodeMigration.class).isActivated());
    }

    public static void assertStatus(JenkinsRule j, boolean migrationDoneGetterValue, Boolean migrationNeededFieldValue, String label, String nodeName) throws Exception {
        assertEquals(migrationDoneGetterValue, j.jenkins.getRenameMigrationDone());
        assertEquals(migrationNeededFieldValue, j.jenkins.nodeRenameMigrationNeeded);
        assertEquals(new LabelAtom(label), j.jenkins.getSelfLabel());
        final FreeStyleProject freeStyleProject = j.createFreeStyleProject();
        final CaptureEnvironmentBuilder environmentBuilder = new CaptureEnvironmentBuilder();
        freeStyleProject.getBuildersList().add(environmentBuilder);
        j.buildAndAssertSuccess(freeStyleProject);
        assertEquals(nodeName, environmentBuilder.getEnvVars().get("NODE_NAME"));
    }
}
