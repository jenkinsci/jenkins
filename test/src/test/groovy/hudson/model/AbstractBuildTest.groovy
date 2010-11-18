/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package hudson.model

import com.gargoylesoftware.htmlunit.Page
import hudson.model.BuildListener
import hudson.slaves.EnvironmentVariablesNodeProperty
import hudson.slaves.EnvironmentVariablesNodeProperty.Entry
import junit.framework.Assert
import org.jvnet.hudson.test.CaptureEnvironmentBuilder
import org.jvnet.hudson.test.GroovyHudsonTestCase

public class AbstractBuildTest extends GroovyHudsonTestCase {
	void testVariablesResolved() {
		def project = createFreeStyleProject();
		hudson.getNodeProperties().replaceBy([
                new EnvironmentVariablesNodeProperty(new Entry("KEY1", "value"), new Entry("KEY2",'$KEY1'))]);
		def builder = new CaptureEnvironmentBuilder();
		project.getBuildersList().add(builder);
		
		buildAndAssertSuccess(project);
		
		def envVars = builder.getEnvVars();
		Assert.assertEquals("value", envVars.get("KEY1"));
		Assert.assertEquals("value", envVars.get("KEY2"));
	}

    /**
     * Makes sure that raw console output doesn't get affected by XML escapes.
     */
    void testRawConsoleOutput() {
        def out = "<test>&</test>";

        def p = createFreeStyleProject();
        p.buildersList.add(builder { builder,launcher,BuildListener listener ->
            listener.logger.println(out);
        })
        def b = buildAndAssertSuccess(p);
        Page rsp = createWebClient().goTo("${b.url}/consoleText", "text/plain");
        println "Output:\n"+rsp.webResponse.contentAsString
        assertTrue(rsp.webResponse.contentAsString.contains(out));
    }
}
