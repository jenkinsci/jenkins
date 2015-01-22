/*
 * The MIT License
 * 
 * Copyright (c) 2004-2010, Sun Microsystems, Inc., Kohsuke Kawaguchi,
 * Alan Harder
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

import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.recipes.LocalData;

import java.net.URL;
import java.util.Enumeration;

/**
 * @author Alan Harder
 */
public class ClassicPluginStrategyTest extends HudsonTestCase {

    @Override
    protected void setUp() throws Exception {
        useLocalPluginManager = true;
        super.setUp();
    }

    /**
     * Test finding resources via DependencyClassLoader.
     */
    @LocalData
    public void testDependencyClassLoader() throws Exception {
        // Test data has: foo3 depends on foo2,foo1; foo2 depends on foo1
        // (thus findResources from foo3 can find foo1 resources via 2 dependency paths)
        PluginWrapper p = jenkins.getPluginManager().getPlugin("foo3");
        String res;

        // In the current impl, the dependencies are the parent ClassLoader so resources
        // are found there before checking the plugin itself.  Adjust the expected results
        // below if this is ever changed to check the plugin first.
        Enumeration<URL> en = p.classLoader.getResources("test-resource");
        for (int i = 0; en.hasMoreElements(); i++) {
            res = en.nextElement().toString();
            if (i < 2)
                assertTrue("In current impl, " + res + "should be foo1 or foo2",
                           res.contains("/foo1/") || res.contains("/foo2/"));
            else
                assertTrue("In current impl, " + res + "should be foo3", res.contains("/foo3/"));
        }
        res = p.classLoader.getResource("test-resource").toString();
        assertTrue("In current impl, " + res + " should be foo1 or foo2",
                   res.contains("/foo1/") || res.contains("/foo2/"));
    }

    /**
     * Test finding resources via DependencyClassLoader.
     * Check transitive dependency exclude disabled plugins
     */
    @LocalData
    @Issue("JENKINS-18654")
    public void testDisabledDependencyClassLoader() throws Exception {
        PluginWrapper p = jenkins.getPluginManager().getPlugin("foo4");

        Enumeration<URL> en = p.classLoader.getResources("test-resource");
        for (int i = 0; en.hasMoreElements(); i++) {
            String res = en.nextElement().toString();
            if (i == 0)
                assertTrue("expected foo4, found "+res , res.contains("/foo4/"));
            else
                fail("disabled dependency should not be included");
        }
    }
}
