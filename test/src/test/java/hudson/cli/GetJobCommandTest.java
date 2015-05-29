/*
 * The MIT License
 *
 * Copyright 2012 Jesse Glick.
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

package hudson.cli;

import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import hudson.model.FreeStyleProject;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Locale;
import org.apache.commons.io.input.NullInputStream;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;

@SuppressWarnings("DM_DEFAULT_ENCODING")
public class GetJobCommandTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Issue("JENKINS-20236")
    @Test public void withFolders() throws Exception {
        MockFolder d = j.createFolder("d");
        FreeStyleProject p = d.createProject(FreeStyleProject.class, "p");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream outS = new PrintStream(out);
        int result = new GetJobCommand().main(Collections.singletonList("d/p"), Locale.ENGLISH, new NullInputStream(0), outS, outS);
        outS.flush();
        String output = out.toString();
        assertEquals(output, 0, result);
        assertEquals(p.getConfigFile().asString(), output);
        out = new ByteArrayOutputStream();
        outS = new PrintStream(out);
        result = new GetJobCommand().main(Collections.singletonList("d"), Locale.ENGLISH, new NullInputStream(0), outS, outS);
        outS.flush();
        output = out.toString();
        assertEquals(output, 0, result);
        assertEquals(d.getConfigFile().asString(), output);
    }

}
