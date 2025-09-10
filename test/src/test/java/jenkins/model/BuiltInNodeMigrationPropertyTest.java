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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

@WithJenkins
class BuiltInNodeMigrationPropertyTest {
    public static final String KEY = Jenkins.class.getName() + ".nodeNameAndSelfLabelOverride";
    public static final String OVERRIDE_VALUE = "foo";

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @BeforeAll
    static void setProperty() {
        System.setProperty(KEY, OVERRIDE_VALUE);
    }

    @AfterAll
    static void clearProperty() {
        System.clearProperty(KEY);
    }

    @Test
    void overrideAppliesToNewInstance() throws Exception {
        BuiltInNodeMigrationTest.assertStatus(j, true, false, OVERRIDE_VALUE, OVERRIDE_VALUE);
    }

    @Test
    @LocalData
    void overrideAppliesToUnmigratedInstance() throws Exception {
        BuiltInNodeMigrationTest.assertStatus(j, false, true, OVERRIDE_VALUE, OVERRIDE_VALUE);
        j.jenkins.performRenameMigration();
        BuiltInNodeMigrationTest.assertStatus(j, true, false, OVERRIDE_VALUE, OVERRIDE_VALUE);
    }

    @Test
    @LocalData
    void overrideAppliesToMigratedInstance() throws Exception {
        BuiltInNodeMigrationTest.assertStatus(j, true, false, OVERRIDE_VALUE, OVERRIDE_VALUE);
    }
}
