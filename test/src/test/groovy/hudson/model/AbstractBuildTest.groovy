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

import hudson.slaves.EnvironmentVariablesNodeProperty
import hudson.slaves.EnvironmentVariablesNodeProperty.Entry

import org.jvnet.hudson.test.CaptureEnvironmentBuilder
import org.jvnet.hudson.test.GroovyHudsonTestCase

import org.jvnet.hudson.test.FakeChangeLogSCM
import org.jvnet.hudson.test.FailureBuilder
import org.jvnet.hudson.test.UnstableBuilder

public class AbstractBuildTest extends GroovyHudsonTestCase {
	void testVariablesResolved() {
		def project = createFreeStyleProject();
		jenkins.nodeProperties.replaceBy([
                new EnvironmentVariablesNodeProperty(new Entry("KEY1", "value"), new Entry("KEY2",'$KEY1'))]);
		def builder = new CaptureEnvironmentBuilder();
		project.buildersList.add(builder);
		
		buildAndAssertSuccess(project);
		
		def envVars = builder.getEnvVars();
		assertEquals("value", envVars["KEY1"]);
		assertEquals("value", envVars["KEY2"]);
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

    def assertCulprits(AbstractBuild b, Collection<String> expectedIds) {
        assertEquals(expectedIds as Set, b.culprits*.id as Set);
    }

    void testCulprits() {

        def p = createFreeStyleProject();
        def scm = new FakeChangeLogSCM()
        p.scm = scm

        // 1st build, successful, no culprits
        scm.addChange().withAuthor("alice")
        def b = assertBuildStatus(Result.SUCCESS,p.scheduleBuild2(0).get())
        assertCulprits(b,["alice"])

        // 2nd build
        scm.addChange().withAuthor("bob")
        p.buildersList.add(new FailureBuilder())
        b = assertBuildStatus(Result.FAILURE,p.scheduleBuild2(0).get())
        assertCulprits(b,["bob"])

        // 3rd build. bob continues to be in culprit
        scm.addChange().withAuthor("charlie")
        b = assertBuildStatus(Result.FAILURE,p.scheduleBuild2(0).get())
        assertCulprits(b,["bob","charlie"])

        // 4th build, unstable. culprit list should continue
        scm.addChange().withAuthor("dave")
        p.buildersList.replaceBy([new UnstableBuilder()])
        b = assertBuildStatus(Result.UNSTABLE,p.scheduleBuild2(0).get())
        assertCulprits(b,["bob","charlie","dave"])

        // 5th build, unstable. culprit list should continue
        scm.addChange().withAuthor("eve")
        b = assertBuildStatus(Result.UNSTABLE,p.scheduleBuild2(0).get())
        assertCulprits(b,["bob","charlie","dave","eve"])

        // 6th build, success, accumulation continues up to this point
        scm.addChange().withAuthor("fred")
        p.buildersList.replaceBy([])
        b = assertBuildStatus(Result.SUCCESS,p.scheduleBuild2(0).get())
        assertCulprits(b,["bob","charlie","dave","eve","fred"])

        // 7th build, back to empty culprits
        scm.addChange().withAuthor("george")
        b = assertBuildStatus(Result.SUCCESS,p.scheduleBuild2(0).get())
        assertCulprits(b,["george"])
    }
}
