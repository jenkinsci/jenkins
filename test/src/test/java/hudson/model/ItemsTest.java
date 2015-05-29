/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package hudson.model;

import java.io.File;
import java.util.Arrays;

import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;

public class ItemsTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public TemporaryFolder tmpRule = new TemporaryFolder();

    @Test public void getAllItems() throws Exception {
        MockFolder d = r.createFolder("d");
        MockFolder sub2 = d.createProject(MockFolder.class, "sub2");
        MockFolder sub2a = sub2.createProject(MockFolder.class, "a");
        MockFolder sub2c = sub2.createProject(MockFolder.class, "c");
        MockFolder sub2b = sub2.createProject(MockFolder.class, "b");
        MockFolder sub1 = d.createProject(MockFolder.class, "sub1");
        FreeStyleProject root = r.createFreeStyleProject("root");
        FreeStyleProject dp = d.createProject(FreeStyleProject.class, "p");
        FreeStyleProject sub1q = sub1.createProject(FreeStyleProject.class, "q");
        FreeStyleProject sub1p = sub1.createProject(FreeStyleProject.class, "p");
        FreeStyleProject sub2ap = sub2a.createProject(FreeStyleProject.class, "p");
        FreeStyleProject sub2bp = sub2b.createProject(FreeStyleProject.class, "p");
        FreeStyleProject sub2cp = sub2c.createProject(FreeStyleProject.class, "p");
        FreeStyleProject sub2alpha = sub2.createProject(FreeStyleProject.class, "alpha");
        FreeStyleProject sub2BRAVO = sub2.createProject(FreeStyleProject.class, "BRAVO");
        FreeStyleProject sub2charlie = sub2.createProject(FreeStyleProject.class, "charlie");
        assertEquals(Arrays.asList(dp, sub1p, sub1q, sub2ap, sub2alpha, sub2bp, sub2BRAVO, sub2cp, sub2charlie), Items.getAllItems(d, FreeStyleProject.class));
        assertEquals(Arrays.<Item>asList(sub2a, sub2ap, sub2alpha, sub2b, sub2bp, sub2BRAVO, sub2c, sub2cp, sub2charlie), Items.getAllItems(sub2, Item.class));
    }

    @Issue("JENKINS-24825")
    @Test public void moveItem() throws Exception {
        File tmp = tmpRule.getRoot();
        r.jenkins.setRawBuildsDir(tmp.getAbsolutePath()+"/${ITEM_FULL_NAME}");
        MockFolder foo = r.createFolder("foo");
        MockFolder bar = r.createFolder("bar");
        FreeStyleProject test = foo.createProject(FreeStyleProject.class, "test");
        test.scheduleBuild2(0).get();
        Items.move(test, bar);
        assertFalse(new File(tmp, "foo/test/1").exists());
        assertTrue(new File(tmp, "bar/test/1").exists());
    }

}
