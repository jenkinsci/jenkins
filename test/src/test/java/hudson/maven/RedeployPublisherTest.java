/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi
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
package hudson.maven;

import hudson.model.Result;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.SingleFileSCM;

import java.io.File;

/**
 * @author Kohsuke Kawaguchi
 */
public class RedeployPublisherTest extends HudsonTestCase {
    @Bug(2593)
    public void testBug2593() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m2 = createMavenProject();
        File repo = createTmpDir();

        // a fake build
        m2.setScm(new SingleFileSCM("pom.xml",getClass().getResource("big-artifact.pom")));
        m2.getPublishersList().add(new RedeployPublisher("",repo.toURI().toString(),true));

        MavenModuleSetBuild b = m2.scheduleBuild2(0).get();
        assertBuildStatus(Result.SUCCESS, b);

        // TODO: confirm that the artifacts use a consistent timestamp
        // TODO: we need to somehow introduce a large delay between deploy since timestamp is only second precision
        // TODO: or maybe we could use a btrace like capability to count the # of invocations?

        System.out.println(repo);
    }


}
