/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Yahoo! Inc.
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
package hudson.tasks.junit;

import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import static hudson.model.Result.UNSTABLE;
import hudson.scm.SubversionSCM;
import hudson.tasks.test.AbstractTestResultAction;
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * @author Kohsuke Kawaguchi
 */
public class CaseResultTest extends HudsonTestCase {
    /**
     * Verifies that Hudson can capture the stdout/stderr output from Maven surefire.
     */
    public void testSurefireOutput() throws Exception {
        setJavaNetCredential();
        configureDefaultMaven();
        
        MavenModuleSet p = createMavenProject();
        p.setScm(new SubversionSCM("https://svn.dev.java.net/svn/hudson/trunk/hudson/test-projects/junit-failure@16411"));
        MavenModuleSetBuild b = assertBuildStatus(UNSTABLE,p.scheduleBuild2(0).get());
        AbstractTestResultAction<?> t = b.getTestResultAction();
        assertSame(1,t.getFailCount());
        CaseResult tc = t.getFailedTests().get(0);
        assertTrue(tc.getStderr().contains("stderr"));
        assertTrue(tc.getStdout().contains("stdout"));
    }
}
