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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import hudson.cli.CLI;
import hudson.model.Item;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Arrays;

/**
 * Tests for ItemListener events.
 * @author Alan.Harder@sun.com
 */
public class ItemListenerTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private StringBuffer events = new StringBuffer();

    @Before
    public void setUp() throws Exception {
        ItemListener listener = new ItemListener() {
            @Override public void onCreated(Item item) {
                events.append('C');
            }
            @Override public void onCopied(Item src, Item item) {
                events.append('Y');
            }
        };
        ItemListener.all().add(0, listener);
    }

    @Test
    public void onCreatedViaCLI() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buf);
        CLI cli = new CLI(j.getURL());
        try {
            cli.execute(Arrays.asList("create-job", "testJob"),
                    new ByteArrayInputStream(("<project><actions/><builders/><publishers/>"
                            + "<buildWrappers/></project>").getBytes()),
                    out, out);
            out.flush();
            assertNotNull("job should be created: " + buf, j.jenkins.getItem("testJob"));
            assertEquals("onCreated event should be triggered: " + buf, "C", events.toString());
        } finally {
            cli.close();
        }
    }
}
