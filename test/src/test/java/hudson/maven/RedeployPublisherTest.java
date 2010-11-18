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
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.SingleFileSCM;
import org.jvnet.hudson.test.Email;

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
        m2.getPublishersList().add(new RedeployPublisher("",repo.toURI().toString(),true, false));

        MavenModuleSetBuild b = m2.scheduleBuild2(0).get();
        assertBuildStatus(Result.SUCCESS, b);

        // TODO: confirm that the artifacts use a consistent timestamp
        // TODO: we need to somehow introduce a large delay between deploy since timestamp is only second precision
        // TODO: or maybe we could use a btrace like capability to count the # of invocations?

        System.out.println(repo);
    }

    public void testConfigRoundtrip() throws Exception {
        MavenModuleSet p = createMavenProject();
        RedeployPublisher rp = new RedeployPublisher("theId", "http://some.url/", true, true);
        p.getPublishersList().add(rp);
        submit(new WebClient().getPage(p,"configure").getFormByName("config"));
        assertEqualBeans(rp,p.getPublishersList().get(RedeployPublisher.class),"id,url,uniqueVersion,evenIfUnstable");
    }

//    /**
//     * Makes sure that the webdav wagon component we bundle is compatible.
//     */
//    public void testWebDavDeployment() throws Exception {
//        configureDefaultMaven();
//        MavenModuleSet m2 = createMavenProject();
//
//        // a fake build
//        m2.setScm(new SingleFileSCM("pom.xml",getClass().getResource("big-artifact.pom")));
//        m2.getPublishersList().add(new RedeployPublisher("","dav:http://localhost/dav/",true));
//
//        MavenModuleSetBuild b = m2.scheduleBuild2(0).get();
//        assertBuildStatus(Result.SUCCESS, b);
//    }

    /**
     * Are we having a problem in handling file names with multiple extensions, like ".tar.gz"?
     */
    @Email("http://www.nabble.com/tar.gz-becomes-.gz-after-Hudson-deployment-td25391364.html")
    @Bug(3814)
    public void testTarGz() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m2 = createMavenProject();
        File repo = createTmpDir();

        // a fake build
        m2.setScm(new SingleFileSCM("pom.xml",getClass().getResource("targz-artifact.pom")));
        m2.getPublishersList().add(new RedeployPublisher("",repo.toURI().toString(),false, false));

        MavenModuleSetBuild b = m2.scheduleBuild2(0).get();
        assertBuildStatus(Result.SUCCESS, b);

        assertTrue("tar.gz doesn't exist",new File(repo,"test/test/0.1-SNAPSHOT/test-0.1-SNAPSHOT-bin.tar.gz").exists());
    }

    @Bug(3773)
    public void testDeployUnstable() throws Exception {
        configureDefaultMaven();
        MavenModuleSet m2 = createMavenProject();
        File repo = createTmpDir();

        // a build with a failing unit tests
        m2.setScm(new ExtractResourceSCM(getClass().getResource("maven-test-failure-findbugs.zip")));
        m2.getPublishersList().add(new RedeployPublisher("",repo.toURI().toString(),false, true));

        MavenModuleSetBuild b = m2.scheduleBuild2(0).get();
        assertBuildStatus(Result.UNSTABLE, b);

        assertTrue("Artifact should have been published even when the build is unstable",
                   new File(repo,"test/test/1.0-SNAPSHOT/test-1.0-SNAPSHOT.jar").exists());
    }
}
