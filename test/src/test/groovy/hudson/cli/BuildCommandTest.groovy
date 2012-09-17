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
package hudson.cli

import org.jvnet.hudson.test.HudsonTestCase
import hudson.tasks.Shell
import hudson.util.OneShotEvent
import org.jvnet.hudson.test.TestBuilder
import hudson.model.AbstractBuild
import hudson.Launcher
import hudson.model.BuildListener
import hudson.model.ParametersDefinitionProperty
import hudson.model.StringParameterDefinition
import hudson.model.ParametersAction
import org.apache.commons.io.output.TeeOutputStream

/**
 * {@link BuildCommand} test.
 *
 * @author Kohsuke Kawaguchi
 */
public class BuildCommandTest extends HudsonTestCase {
    /**
     * Just schedules a build and return.
     */
    void testAsync() {
        def p = createFreeStyleProject();
        def started = new OneShotEvent();
        def completed = new OneShotEvent();
        p.buildersList.add([perform: {AbstractBuild build, Launcher launcher, BuildListener listener ->
            started.signal();
            completed.block();
            return true;
        }] as TestBuilder);

        // this should be asynchronous
        def cli = new CLI(getURL())
        try {
            assertEquals(0,cli.execute(["build", p.name]))
            started.block()
            assertTrue(p.getBuildByNumber(1).isBuilding())
            completed.signal()
        } finally {
            cli.close();
        }

    }

    /**
     * Tests synchronous execution.
     */
    void testSync() {
        def p = createFreeStyleProject();
        p.buildersList.add(new Shell("sleep 3"));

        def cli = new CLI(getURL())
        try {
            cli.execute(["build","-s",p.name])
            assertFalse(p.getBuildByNumber(1).isBuilding())
        } finally {
            cli.close();
        }

    }

    /**
     * Tests synchronous execution with retried verbose output
     */
    void testSyncWOutputStreaming() {
        def p = createFreeStyleProject();
        p.buildersList.add(new Shell("sleep 3"));

        def cli =new CLI(getURL())
        try {
            cli.execute(["build","-s","-v","-r","5",p.name])
            assertFalse(p.getBuildByNumber(1).isBuilding())
        } finally {
            cli.close();
        }
    }

    void testParameters() {
        def p = createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty([new StringParameterDefinition("key",null)]));

        def cli = new CLI(getURL())
        try {
            cli.execute(["build","-s","-p","key=foobar",p.name])
            def b = assertBuildStatusSuccess(p.getBuildByNumber(1))
            assertEquals("foobar",b.getAction(ParametersAction.class).getParameter("key").value)
        } finally {
            cli.close();
        }
    }

    void testDefaultParameters() {
        def p = createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty([new StringParameterDefinition("key","default"), new StringParameterDefinition("key2","default2") ]));

        def cli = new CLI(getURL())
        try {
            cli.execute(["build","-s","-p","key=foobar",p.name])
            def b = assertBuildStatusSuccess(p.getBuildByNumber(1))
            assertEquals("foobar",b.getAction(ParametersAction.class).getParameter("key").value)
            assertEquals("default2",b.getAction(ParametersAction.class).getParameter("key2").value)
        } finally {
            cli.close();
        }
    }

    void testConsoleOutput() {
        def p = createFreeStyleProject()
        def cli = new CLI(getURL())
        try {
            def o = new ByteArrayOutputStream()
            cli.execute(["build","-s","-v",p.name],System.in,new TeeOutputStream(System.out,o),System.err)
            assertBuildStatusSuccess(p.getBuildByNumber(1))
            assertTrue(o.toString(), o.toString().contains("Started by command line by anonymous"))
            assertTrue(o.toString().contains("Finished: SUCCESS"))
        } finally {
            cli.close()
        }
    }
}