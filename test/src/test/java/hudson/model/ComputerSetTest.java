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

import hudson.cli.CLI;
import hudson.slaves.DumbSlave;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.Bug;
import com.gargoylesoftware.htmlunit.html.HtmlForm;

/**
 * @author Kohsuke Kawaguchi
 */
public class ComputerSetTest extends HudsonTestCase {
    @Bug(2821)
    public void testPageRendering() throws Exception {
        HudsonTestCase.WebClient client = new WebClient();
        createSlave();
        client.goTo("computer");
    }

    /**
     * Tests the basic UI behavior of the node monitoring
     */
    public void testConfiguration() throws Exception {
        HudsonTestCase.WebClient client = new WebClient();
        HtmlForm form = client.goTo("computer/configure").getFormByName("config");
        submit(form);
    }

    public void testNodeOfflineCli() throws Exception {
        DumbSlave s = createSlave();

        CLI cli = new CLI(getURL());
        try {
            assertTrue(cli.execute("wait-node-offline","xxx")!=0);
            assertTrue(cli.execute("wait-node-online",s.getNodeName())==0);

            s.toComputer().disconnect().get();

            assertTrue(cli.execute("wait-node-offline",s.getNodeName())==0);
        } finally {
            cli.close();
        }
    }
}
