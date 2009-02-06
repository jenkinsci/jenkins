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
package hudson.tasks;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.FreeStyleProject;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class MavenTest extends HudsonTestCase {
    /**
     * Tests the round-tripping of the configuration.
     */
    public void testConfigRoundtrip() throws Exception {
        Maven.DESCRIPTOR.setInstallations(); // reset

        FreeStyleProject p = createFreeStyleProject();
        p.getBuildersList().add(new Maven("a",null,"b.pom","c=d","-e"));

        WebClient webClient = new WebClient();
        HtmlPage page = webClient.getPage(p,"configure");

        HtmlForm form = page.getFormByName("config");
        submit(form);

        Maven m = (Maven)p.getBuildersList().get(Maven.DESCRIPTOR);
        assertNotNull(m);
        assertEquals("a",m.targets);
        assertNull("found "+m.mavenName,m.mavenName);
        assertEquals("b.pom",m.pom);
        assertEquals("c=d",m.properties);
        assertEquals("-e",m.jvmOptions);
    }
}
