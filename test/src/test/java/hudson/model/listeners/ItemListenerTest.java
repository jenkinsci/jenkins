/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Alan Harder
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

package hudson.model.listeners;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import hudson.cli.CLICommandInvoker;
import hudson.model.Item;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Tests for ItemListener events.
 * @author Alan.Harder@sun.com
 */
public class ItemListenerTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private StringBuffer events = new StringBuffer();

    @Before
    public void setUp() {
        ItemListener listener = new ItemListener() {
            @Override public void onCreated(Item item) {
                events.append('C');
            }

            @Override public void onCopied(Item src, Item item) {
                events.append('Y');
            }

            @Override
            public void onUpdated(Item item) {
                events.append('U');
            }

        };
        ItemListener.all().add(0, listener);
    }

    @Test
    public void onCreatedViaCLI() {
        CLICommandInvoker.Result result = new CLICommandInvoker(j, "create-job").
                withStdin(new ByteArrayInputStream("<project><actions/><builders/><publishers/><buildWrappers/></project>".getBytes(Charset.defaultCharset()))).
                invokeWithArgs("testJob");
        assertThat(result, CLICommandInvoker.Matcher.succeeded());
        assertNotNull("job should be created: " + result, j.jenkins.getItem("testJob"));
        assertEquals("onCreated event should be triggered: " + result, "C", events.toString());
    }

    @Issue("JENKINS-64553")
    @Test
    public void onUpdatedViaCLI() {
        CLICommandInvoker.Result result = new CLICommandInvoker(j, "create-job").
                withStdin(new ByteArrayInputStream("<project/>".getBytes(Charset.defaultCharset()))).
                invokeWithArgs("testJob");
        assertThat(result, CLICommandInvoker.Matcher.succeeded());
        result = new CLICommandInvoker(j, "update-job").
                withStdin(new ByteArrayInputStream("<project><actions/><builders/><publishers/><buildWrappers/></project>".getBytes(Charset.defaultCharset()))).
                invokeWithArgs("testJob");
        assertThat(result, CLICommandInvoker.Matcher.succeeded());
        assertEquals("onUpdated event should be triggered: " + result, "CU", events.toString());
    }
}
