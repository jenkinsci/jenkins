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
package hudson;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.apache.commons.io.FileUtils;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.WithPlugin;

import java.io.File;

/**
 * @author Kohsuke Kawaguchi
 */
public class PluginManagerTest extends HudsonTestCase {
    /**
     * Manual submission form.
     */
    public void testUpload() throws Exception {
        HtmlPage page = new WebClient().goTo("pluginManager/advanced");
        HtmlForm f = page.getFormByName("uploadPlugin");
        File dir = env.temporaryDirectoryAllocator.allocate();
        File plugin = new File(dir, "tasks.hpi");
        FileUtils.copyURLToFile(getClass().getClassLoader().getResource("plugins/tasks.hpi"),plugin);
        f.getInputByName("name").setValueAttribute(plugin.getAbsolutePath());
        submit(f);

        assertTrue( new File(hudson.getRootDir(),"plugins/tasks.hpi").exists() );
    }

    /**
     * Tests the effect of {@link WithPlugin}.
     */
    @WithPlugin("tasks.hpi")
    public void testWithRecipe() throws Exception {
        assertNotNull(hudson.getPlugin("tasks"));
    }

    /**
     * Makes sure that plugins can see Maven2 plugin that's refactored out in 1.296.
     */
    @WithPlugin("tasks.hpi")
    public void testOptionalMavenDependency() throws Exception {
        PluginWrapper.Dependency m2=null;
        PluginWrapper tasks = hudson.getPluginManager().getPlugin("tasks");
        for( PluginWrapper.Dependency d : tasks.getOptionalDependencies() ) {
            if(d.shortName.equals("maven-plugin")) {
                assertNull(m2);
                m2 = d;
            }
        }
        assertNotNull(m2);

        // this actually doesn't really test what we need, though, because
        // I thought test harness is loading the maven classes by itself.
        // TODO: write a separate test that tests the optional dependency loading
        tasks.classLoader.loadClass(hudson.maven.agent.AbortException.class.getName());
    }
}
