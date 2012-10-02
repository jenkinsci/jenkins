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
package hudson.model;

import hudson.FilePath;
import hudson.Functions;
import hudson.tasks.Shell;
import hudson.tasks.BatchFile;
import hudson.Launcher;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TestBuilder;

import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class DirectoryBrowserSupportTest extends HudsonTestCase {
    /**
     * Double dots that appear in file name is OK.
     */
    @Email("http://www.nabble.com/Status-Code-400-viewing-or-downloading-artifact-whose-filename-contains-two-consecutive-periods-tt21407604.html")
    public void testDoubleDots() throws Exception {
        // create a problematic file name in the workspace
        FreeStyleProject p = createFreeStyleProject();
        if(Functions.isWindows())
            p.getBuildersList().add(new BatchFile("echo > abc..def"));
        else
            p.getBuildersList().add(new Shell("touch abc..def"));
        p.scheduleBuild2(0).get();

        // can we see it?
        new WebClient().goTo("job/"+p.getName()+"/ws/abc..def","application/octet-stream");

        // TODO: implement negative check to make sure we aren't serving unexpected directories.
        // the following trivial attempt failed. Someone in between is normalizing.
//        // but this should fail
//        try {
//            new WebClient().goTo("job/" + p.getName() + "/ws/abc/../", "application/octet-stream");
//        } catch (FailingHttpStatusCodeException e) {
//            assertEquals(400,e.getStatusCode());
//        }
    }

    /**
     * <strike>Also makes sure '\\' in the file name for Unix is handled correctly</strike>.
     *
     * To prevent directory traversal attack, we now treat '\\' just like '/'.
     */
    @Email("http://www.nabble.com/Status-Code-400-viewing-or-downloading-artifact-whose-filename-contains-two-consecutive-periods-tt21407604.html")
    public void testDoubleDots2() throws Exception {
        if(Functions.isWindows())  return; // can't test this on Windows

        // create a problematic file name in the workspace
        FreeStyleProject p = createFreeStyleProject();
        p.getBuildersList().add(new Shell("mkdir abc; touch abc/def.bin"));
        p.scheduleBuild2(0).get();

        // can we see it?
        new WebClient().goTo("job/"+p.getName()+"/ws/abc%5Cdef.bin","application/octet-stream");
    }

    public void testNonAsciiChar() throws Exception {
        // create a problematic file name in the workspace
        FreeStyleProject p = createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                build.getWorkspace().child("\u6F22\u5B57.bin").touch(0); // Kanji
                return true;
            }
        }); // Kanji
        p.scheduleBuild2(0).get();

        // can we see it?
        new WebClient().goTo("job/"+p.getName()+"/ws/%e6%bc%a2%e5%ad%97.bin","application/octet-stream");
    }

    public void testGlob() throws Exception {
        FreeStyleProject p = createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                FilePath ws = build.getWorkspace();
                ws.child("pom.xml").touch(0);
                ws.child("src/main/java/p").mkdirs();
                ws.child("src/main/java/p/X.java").touch(0);
                ws.child("src/main/resources/p").mkdirs();
                ws.child("src/main/resources/p/x.txt").touch(0);
                ws.child("src/test/java/p").mkdirs();
                ws.child("src/test/java/p/XTest.java").touch(0);
                return true;
            }
        });
        assertEquals(Result.SUCCESS, p.scheduleBuild2(0).get().getResult());
        String text = new WebClient().goTo("job/"+p.getName()+"/ws/**/*.java").asText();
        assertTrue(text, text.contains("X.java"));
        assertTrue(text, text.contains("XTest.java"));
        assertFalse(text, text.contains("pom.xml"));
        assertFalse(text, text.contains("x.txt"));
    }

}
