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

import java.io.IOException;

import com.gargoylesoftware.htmlunit.Page

import hudson.Launcher;
import hudson.slaves.EnvironmentVariablesNodeProperty
import hudson.slaves.EnvironmentVariablesNodeProperty.Entry

import org.jvnet.hudson.test.CaptureEnvironmentBuilder
import org.jvnet.hudson.test.GroovyJenkinsRule
import org.jvnet.hudson.test.FakeChangeLogSCM
import org.jvnet.hudson.test.FailureBuilder
import org.jvnet.hudson.test.UnstableBuilder

import static org.junit.Assert.*
import static org.hamcrest.CoreMatchers.*;

import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.Issue

public class AbstractBuildTest {

    @Rule public GroovyJenkinsRule j = new GroovyJenkinsRule();

    @Test void variablesResolved() {
		def project = j.createFreeStyleProject();
		j.jenkins.nodeProperties.replaceBy([
                new EnvironmentVariablesNodeProperty(new Entry("KEY1", "value"), new Entry("KEY2",'$KEY1'))]);
		def builder = new CaptureEnvironmentBuilder();
		project.buildersList.add(builder);
		
		j.buildAndAssertSuccess(project);
		
		def envVars = builder.getEnvVars();
		assertEquals("value", envVars["KEY1"]);
		assertEquals("value", envVars["KEY2"]);
	}

    /**
     * Makes sure that raw console output doesn't get affected by XML escapes.
     */
    @Test void rawConsoleOutput() {
        def out = "<test>&</test>";

        def p = j.createFreeStyleProject();
        p.buildersList.add(j.builder { builder,launcher,BuildListener listener ->
            listener.logger.println(out);
        })
        def b = j.buildAndAssertSuccess(p);
        Page rsp = j.createWebClient().goTo("${b.url}/consoleText", "text/plain");
        println "Output:\n"+rsp.webResponse.contentAsString
        assertTrue(rsp.webResponse.contentAsString.contains(out));
    }

    def assertCulprits(AbstractBuild b, Collection<String> expectedIds) {
        assertEquals(expectedIds as Set, b.culprits*.id as Set);
    }

    @Test void culprits() {

        def p = j.createFreeStyleProject();
        def scm = new FakeChangeLogSCM()
        p.scm = scm

        // 1st build, successful, no culprits
        scm.addChange().withAuthor("alice")
        def b = j.assertBuildStatus(Result.SUCCESS,p.scheduleBuild2(0).get())
        assertCulprits(b,["alice"])

        // 2nd build
        scm.addChange().withAuthor("bob")
        p.buildersList.add(new FailureBuilder())
        b = j.assertBuildStatus(Result.FAILURE,p.scheduleBuild2(0).get())
        assertCulprits(b,["bob"])

        // 3rd build. bob continues to be in culprit
        scm.addChange().withAuthor("charlie")
        b = j.assertBuildStatus(Result.FAILURE,p.scheduleBuild2(0).get())
        assertCulprits(b,["bob","charlie"])

        // 4th build, unstable. culprit list should continue
        scm.addChange().withAuthor("dave")
        p.buildersList.replaceBy([new UnstableBuilder()])
        b = j.assertBuildStatus(Result.UNSTABLE,p.scheduleBuild2(0).get())
        assertCulprits(b,["bob","charlie","dave"])

        // 5th build, unstable. culprit list should continue
        scm.addChange().withAuthor("eve")
        b = j.assertBuildStatus(Result.UNSTABLE,p.scheduleBuild2(0).get())
        assertCulprits(b,["bob","charlie","dave","eve"])

        // 6th build, success, accumulation continues up to this point
        scm.addChange().withAuthor("fred")
        p.buildersList.replaceBy([])
        b = j.assertBuildStatus(Result.SUCCESS,p.scheduleBuild2(0).get())
        assertCulprits(b,["bob","charlie","dave","eve","fred"])

        // 7th build, back to empty culprits
        scm.addChange().withAuthor("george")
        b = j.assertBuildStatus(Result.SUCCESS,p.scheduleBuild2(0).get())
        assertCulprits(b,["george"])
    }

    @Issue("JENKINS-19920")
    @Test void lastBuildNextBuild() {
        FreeStyleProject p = j.createFreeStyleProject();
        AbstractBuild b1 = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        AbstractBuild b2 = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(b2, p.getLastBuild());
        b2.getNextBuild(); // force this to be initialized
        b2.delete();
        assertEquals(b1, p.getLastBuild());
        b1 = p.getLastBuild();
        assertEquals(null, b1.getNextBuild());
    }

    @Test void doNotInteruptBuildAbruptlyWhenExceptionThrownFromBuildStep() {
        FreeStyleProject p = j.createFreeStyleProject();
        p.getBuildersList().add(new ThrowBuilder());
        def build = p.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.FAILURE, build);

        def log = build.log;
        assertThat(log, containsString("Finished: FAILURE"));
        assertThat(log, containsString("Build step 'Bogus' marked build as failure"));
    }

    private static class ThrowBuilder extends org.jvnet.hudson.test.TestBuilder {
        @Override public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            throw new NullPointerException();
        }
    }
}
