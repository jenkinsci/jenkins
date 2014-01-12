/*
 * The MIT License
 *
 * Copyright (c) 2013 Red Hat, Inc.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Slave;
import hudson.model.StringParameterDefinition;
import hudson.tasks.BatchFile;
import hudson.tasks.CommandInterpreter;
import hudson.tasks.Shell;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.JenkinsRule;

public class LauncherTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Bug(19488)
    @Test
    public void correctlyExpandEnvVars() throws Exception {
        FreeStyleProject project = rule.createFreeStyleProject();
        project.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("A", "aaa"),
                new StringParameterDefinition("C", "ccc"),
                new StringParameterDefinition("B", "$A$C")
        ));
        final CommandInterpreter script = Functions.isWindows()
                ? new BatchFile("echo %A% %B% %C%")
                : new Shell("echo $A $B $C")
        ;
        project.getBuildersList().add(script);

        FreeStyleBuild build = project.scheduleBuild2(0).get();

        assertThat(log(build), containsString("aaa aaaccc ccc"));
    }
    
    @Bug(19926)
    @Test
    public void overwriteSystemEnvVars() throws Exception {
        Map<String, String> env = new HashMap<String,String>();
        env.put("jenkins_19926", "original value");
        Slave slave = rule.createSlave(new EnvVars(env));
        
        FreeStyleProject project = rule.createFreeStyleProject();
        project.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("jenkins_19926", "${jenkins_19926} and new value")));
        final CommandInterpreter script = Functions.isWindows()
                ? new BatchFile("echo %jenkins_19926")
                : new Shell("echo ${jenkins_19926}")
        ;
        project.getBuildersList().add(script);
        project.setAssignedNode(slave.getComputer().getNode());

        FreeStyleBuild build = project.scheduleBuild2(0).get();

        assertThat(log(build), containsString("original value and new value"));
    }

    @SuppressWarnings("deprecation")
    private String log(FreeStyleBuild build) throws IOException {
        return build.getLog();
    }
}
