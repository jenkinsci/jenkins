/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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
package hudson;

import hudson.model.Node;
import hudson.slaves.DumbSlave;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@For(FilePath.class)
public class FilePathTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void shouldListFilesOnAgent() throws Exception {
        final DumbSlave agent = j.createOnlineSlave();
        FilePath p = agent.getRootPath();
        FilePath foo = p.child("foo.txt");
        foo.write("Hello, JEP-200!", "UTF-8");
        FilePath[] paths = p.list("*.txt");
        assertThat("1 file should be found", paths.length, equalTo(1));
        assertThat(paths[0].getRemote(), containsString("foo.txt"));
    }

    @Test
    public void shouldListFilesOnMaster_nonExistentPath() throws Exception {
        assertFailsToReadNonExistetDir(j.jenkins);
    }

    @Test
    @Issue("JENKINS-50237")
    public void shouldListFilesOnAgent_nonExistentPath() throws Exception {
        final DumbSlave agent = j.createOnlineSlave();
        assertFailsToReadNonExistetDir(agent);
    }

    private void assertFailsToReadNonExistetDir(Node agent) throws AssertionError, InterruptedException {
        try {
            FilePath p = agent.getRootPath();
            FilePath[] paths = p.child("/non/existent/path").list("*.txt");
        } catch (IOException ex) {
            assertThat("FilePath#list() should have failed with standard error",
                    ex.getMessage(), containsString("Failed to scan directory"));
            return;
        }
        fail("Expected IOException");
    }

}
