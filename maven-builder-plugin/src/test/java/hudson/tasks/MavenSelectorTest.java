/*
 * The MIT License
 * 
 * Copyright (c) 2016 CloudBess, Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.Extension;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.tasks.Maven.MavenInstallation;
import jenkins.model.Jenkins;

public class MavenSelectorTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();
    private static MavenInstallation TEST_MAVEN_INSTALLATION = new MavenInstallation("TEST_MAVEN_NAME", null, null);

    @Extension
    public static class MavenSelectorExtensionTest extends MavenSelector {

        @Override
        public MavenInstallation selectMavenInstallation(Item item) {
            if (item instanceof TestItem) {
                return TEST_MAVEN_INSTALLATION;
            } else {
                return null;
            }
        }

        @Override
        public boolean isApplicable(Class<? extends Item> jobType) {
            if (jobType.isAssignableFrom(TestItem.class)) {
                return true;
            }
            return false;
        }
    }

    public static class TestItem extends FreeStyleProject {
        String mavenInstallationName = "MyMavenName";
        public TestItem(ItemGroup parent, String name) {
            super(parent, name);
        }
    }

    @Test
    public void autodiscovery() {
        assertNotEquals(0, MavenSelector.all().size());
    }

    @Test
    public void obtainMavenInstallationName() {
        TestItem testItem = new TestItem(Jenkins.getInstance(), "testItem");
        MavenInstallation mi = MavenSelector.obtainMavenInstallation(testItem);
        assertEquals(TEST_MAVEN_INSTALLATION, mi);
    }

}
