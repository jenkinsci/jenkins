/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

package hudson.model;

import hudson.EnvVars;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;

public class RunParameterDefinitionTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Bug(16462)
    @Test public void inFolders() throws Exception {
        MockFolder dir = j.createFolder("dir");
        MockFolder subdir = dir.createProject(MockFolder.class, "sub dir");
        FreeStyleProject p = subdir.createProject(FreeStyleProject.class, "some project");
        p.scheduleBuild2(0).get();
        FreeStyleBuild build2 = p.scheduleBuild2(0).get();
        p.scheduleBuild2(0).get();
        String id = build2.getExternalizableId();
        assertEquals("dir/sub dir/some project#2", id);
        assertEquals(build2, Run.fromExternalizableId(id));
        RunParameterDefinition def = new RunParameterDefinition("build", "dir/sub dir/some project", "my build");
        assertEquals("dir/sub dir/some project", def.getProjectName());
        assertEquals(p, def.getProject());
        EnvVars env = new EnvVars();
        def.getDefaultParameterValue().buildEnvVars(null, env);
        assertEquals(j.jenkins.getRootUrl() + "job/dir/job/sub%20dir/job/some%20project/3/", env.get("build"));
        RunParameterValue val = def.createValue(id);
        assertEquals(build2, val.getRun());
        assertEquals("dir/sub dir/some project", val.getJobName());
        assertEquals("2", val.getNumber());
        val.buildEnvVars(null, env);
        assertEquals(j.jenkins.getRootUrl() + "job/dir/job/sub%20dir/job/some%20project/2/", env.get("build"));
        assertEquals("dir/sub dir/some project", env.get("build.jobName"));
        assertEquals("2", env.get("build.number"));
    }

}
