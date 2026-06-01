/*
 * The MIT License
 *
 * Copyright (c) 2010, Kohsuke Kawaguchi
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

package hudson.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import hudson.Functions;
import hudson.Launcher.LocalLauncher;
import hudson.Launcher.RemoteLauncher;
import hudson.Proc;
import hudson.model.Slave;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.logging.Level;
import jenkins.util.SystemProperties;
import org.apache.tools.ant.util.JavaEnvUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class ArgumentListBuilder2Test {

    private final LogRecorder logging = new LogRecorder().
        record(StreamTaskListener.class, Level.FINE).
        record(SystemProperties.class, Level.FINE);

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Makes sure {@link RemoteLauncher} properly masks arguments.
     */
    @Test
    @Email("http://n4.nabble.com/Password-masking-when-running-commands-on-a-slave-tp1753033p1753033.html")
    void slaveMask() throws Exception {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("java");
        args.addMasked("-version");

        Slave s = j.createOnlineSlave();
        j.showAgentLogs(s, logging);

        StringWriter out = new StringWriter();
        assertEquals(0, s.createLauncher(new StreamTaskListener(out)).launch().cmds(args).join());
        assertThat(out.toString(), containsString("$ java ********"));
    }

    @Test
    void ensureArgumentsArePassedViaCmdExeUnmodified() throws Exception {
        assumeTrue(Functions.isWindows());

        String[] specials = new String[] {
                "~",
                "!",
                "@",
                "#",
                "$",
                "%",
                "^",
                "&",
                "*",
                "(",
                ")",
                "_",
                "+",
                "{",
                "}",
                "[",
                "]",
                ":",
                ";",
                "\"",
                "'",
                "\\",
                "|",
                "<",
                ">",
                ",",
                ".",
                "/",
                "?",
                " ",
        };

        String out = echoArgs(specials);

        String expected = String.format("%n%s", String.join(" ", specials));
        assertThat(out, containsString(expected));
    }

    public String echoArgs(String... arguments) throws Exception {
        String testHarnessJar = new File(Class.forName("hudson.util.EchoCommand")
                .getProtectionDomain()
                .getCodeSource()
                .getLocation().toURI()).getAbsolutePath();

        ArgumentListBuilder args = new ArgumentListBuilder(
                    JavaEnvUtils.getJreExecutable("java").replaceAll("^\"|\"$", ""),
                    "-cp", testHarnessJar, "hudson.util.EchoCommand")
                .add(arguments)
                .toWindowsCommand();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        final StreamTaskListener listener = new StreamTaskListener(out, Charset.defaultCharset());
        Proc p = new LocalLauncher(listener)
                .launch()
                .stderr(System.err)
                .stdout(out)
                .cmds(args)
                .start()
        ;
        int code = p.join();
        listener.close();

        assumeTrue(code == 0, "Failed to run " + args);
        return out.toString(Charset.defaultCharset());
    }
}
