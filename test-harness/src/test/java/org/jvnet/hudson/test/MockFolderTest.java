/*
 * The MIT License
 *
 * Copyright 2012 Jesse Glick.
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

package org.jvnet.hudson.test;

import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Items;
import hudson.model.listeners.ItemListener;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;

public class MockFolderTest {
    
    @Rule public JenkinsRule j = new JenkinsRule();

    @Test public void basics() throws Exception {
        MockFolder dir = j.createFolder("dir");
        FreeStyleProject p = dir.createProject(FreeStyleProject.class, "p");
        assertEquals("dir/p", p.getFullName());
    }

    @Test public void moving() throws Exception {
        MockFolder top = j.createFolder("top");
        FreeStyleProject p = top.createProject(FreeStyleProject.class, "p");
        MockFolder sub = top.createProject(MockFolder.class, "sub");
        assertNews("created=top created=top/p created=top/sub");
        Items.move(p, j.jenkins);
        assertEquals(j.jenkins, p.getParent());
        assertEquals(p, j.jenkins.getItem("p"));
        assertNull(top.getItem("p"));
        assertNews("moved=p;from=top/p");
        Items.move(p, sub);
        assertEquals(sub, p.getParent());
        assertEquals(p, sub.getItem("p"));
        assertNull(j.jenkins.getItem("p"));
        assertNews("moved=top/sub/p;from=p");
        Items.move(sub, j.jenkins);
        assertEquals(sub, p.getParent());
        assertEquals(p, sub.getItem("p"));
        assertEquals(j.jenkins, sub.getParent());
        assertEquals(sub, j.jenkins.getItem("sub"));
        assertNull(top.getItem("sub"));
        assertNews("moved=sub;from=top/sub moved=sub/p;from=top/sub/p");
        Items.move(sub, top);
        assertNews("moved=top/sub;from=sub moved=top/sub/p;from=sub/p");
        assertEquals(sub, top.getItem("sub"));
        sub.renameTo("lower");
        assertNews("renamed=top/lower;from=sub moved=top/lower;from=top/sub moved=top/lower/p;from=top/sub/p");
        top.renameTo("upper");
        assertNews("renamed=upper;from=top moved=upper;from=top moved=upper/lower;from=top/lower moved=upper/lower/p;from=top/lower/p");
        assertEquals(p, sub.getItem("p"));
        p.renameTo("j");
        assertNews("renamed=upper/lower/j;from=p moved=upper/lower/j;from=upper/lower/p");
        top.renameTo("upperz");
        assertNews("renamed=upperz;from=upper moved=upperz;from=upper moved=upperz/lower;from=upper/lower moved=upperz/lower/j;from=upper/lower/j");
        assertEquals(sub, top.getItem("lower"));
        sub.renameTo("upperzee");
        assertNews("renamed=upperz/upperzee;from=lower moved=upperz/upperzee;from=upperz/lower moved=upperz/upperzee/j;from=upperz/lower/j");
        Items.move(sub, j.jenkins);
        assertNews("moved=upperzee;from=upperz/upperzee moved=upperzee/j;from=upperz/upperzee/j");
        assertEquals(p, j.jenkins.getItemByFullName("upperzee/j"));
    }
    private void assertNews(String expected) {
        L l = j.jenkins.getExtensionList(ItemListener.class).get(L.class);
        assertEquals(expected, l.b.toString().trim());
        l.b.delete(0, l.b.length());
    }
    @TestExtension("moving") public static class L extends ItemListener {
        final StringBuilder b = new StringBuilder();
        @Override public void onCreated(Item item) {
            b.append(" created=").append(item.getFullName());
        }
        @Override public void onDeleted(Item item) {
            b.append(" deleted=").append(item.getFullName());
        }
        @Override public void onRenamed(Item item, String oldName, String newName) {
            assertEquals(item.getName(), newName);
            b.append(" renamed=").append(item.getFullName()).append(";from=").append(oldName);
        }
        @Override public void onLocationChanged(Item item, String oldFullName, String newFullName) {
            assertEquals(item.getFullName(), newFullName);
            b.append(" moved=").append(newFullName).append(";from=").append(oldFullName);
        }
    }

}
