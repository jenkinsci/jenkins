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
import hudson.tasks.Maven.MavenInstallation;

import org.apache.commons.lang.StringUtils;
import org.jvnet.hudson.test.Bug;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.SingleFileSCM;
import org.jvnet.hudson.test.Email;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import static org.junit.Assert.*;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Kohsuke Kawaguchi
 */
public class RedeployPublisherTest {

    @Rule public JenkinsRule j = new JenkinsRule();
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Bug(2593)
    @Test
    public void testBug2593() throws Exception {
        Assume.assumeFalse("Not a v4.0.0 POM. for project org.jvnet.maven-antrun-extended-plugin:maven-antrun-extended-plugin at /home/jenkins/.m2/repository/org/jvnet/maven-antrun-extended-plugin/maven-antrun-extended-plugin/1.39/maven-antrun-extended-plugin-1.39.pom", "https://jenkins.ci.cloudbees.com/job/core/job/jenkins_main_trunk/".equals(System.getenv("JOB_URL")));
        j.configureDefaultMaven();
        MavenModuleSet m2 = j.createMavenProject();
        File repo = tmp.getRoot();

        // a fake build
        m2.setScm(new SingleFileSCM("pom.xml",getClass().getResource("big-artifact.pom")));
        m2.getPublishersList().add(new RedeployPublisher("",repo.toURI().toString(),true, false));

        MavenModuleSetBuild b = m2.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.SUCCESS, b);

        // TODO: confirm that the artifacts use a consistent timestamp
        // TODO: we need to somehow introduce a large delay between deploy since timestamp is only second precision
        // TODO: or maybe we could use a btrace like capability to count the # of invocations?

        System.out.println(repo);
    }

    @Test
    public void testConfigRoundtrip() throws Exception {
        MavenModuleSet p = j.createMavenProject();
        RedeployPublisher rp = new RedeployPublisher("theId", "http://some.url/", true, true);
        p.getPublishersList().add(rp);
        j.submit(j.new WebClient().getPage(p,"configure").getFormByName("config"));
        j.assertEqualBeans(rp,p.getPublishersList().get(RedeployPublisher.class),"id,url,uniqueVersion,evenIfUnstable");
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
    @Test
    public void testTarGz() throws Exception {
        j.configureDefaultMaven();
        MavenModuleSet m2 = j.createMavenProject();
        File repo = tmp.getRoot();

        // a fake build
        m2.setScm(new SingleFileSCM("pom.xml",getClass().getResource("targz-artifact.pom")));
        m2.getPublishersList().add(new RedeployPublisher("",repo.toURI().toString(),false, false));

        MavenModuleSetBuild b = m2.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.SUCCESS, b);

        assertTrue("tar.gz doesn't exist",new File(repo,"test/test/0.1-SNAPSHOT/test-0.1-SNAPSHOT-bin.tar.gz").exists());
    }
    
    @Test
    public void testTarGzUniqueVersionTrue() throws Exception {
        j.configureDefaultMaven();
        MavenModuleSet m2 = j.createMavenProject();
        File repo = tmp.getRoot();
        
        // a fake build
        m2.setScm(new SingleFileSCM("pom.xml",getClass().getResource("targz-artifact.pom")));
        m2.getPublishersList().add(new RedeployPublisher("",repo.toURI().toString(),true, false));

        MavenModuleSetBuild b = m2.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.SUCCESS, b);

        File artifactDir = new File(repo,"test/test/0.1-SNAPSHOT/");
        String[] files = artifactDir.list( new FilenameFilter()
        {
            
            public boolean accept( File dir, String name )
            {
                System.out.print( "deployed file " + name );
                return name.contains( "-bin.tar.gz" ) || name.endsWith( ".jar" ) || name.endsWith( "-bin.zip" );
            }
        });
        System.out.println("deployed files " + Arrays.asList( files ));
        assertFalse("tar.gz doesn't exist",new File(repo,"test/test/0.1-SNAPSHOT/test-0.1-SNAPSHOT-bin.tar.gz").exists());
        assertTrue("tar.gz doesn't exist",!files[0].contains( "SNAPSHOT" ));
        for (String file : files) {
            if (file.endsWith( "-bin.tar.gz" )) {
                String ver = StringUtils.remove( file, "-bin.tar.gz" );
                ver = ver.substring( ver.length() - 1, ver.length() );
                assertEquals("-bin.tar.gz not ended with 1 , file " + file , "1", ver);
            }
            if (file.endsWith( ".jar" )) {
                String ver = StringUtils.remove( file, ".jar" );
                ver = ver.substring( ver.length() - 1, ver.length() );
                assertEquals(".jar not ended with 1 , file " + file , "1", ver);
            }            
            if (file.endsWith( "-bin.zip" )) {
                String ver = StringUtils.remove( file, "-bin.zip" );
                ver = ver.substring( ver.length() - 1, ver.length() );
                assertEquals("-bin.zip not ended with 1 , file " + file , "1", ver);
            }            
        }        
        
    }    
    
    @Test
    public void testTarGzMaven3() throws Exception {
        
        MavenModuleSet m3 = j.createMavenProject();
        MavenInstallation mvn = j.configureMaven3();
        m3.setMaven( mvn.getName() );
        File repo = tmp.getRoot();
        // a fake build
        m3.setScm(new SingleFileSCM("pom.xml",getClass().getResource("targz-artifact.pom")));
        m3.getPublishersList().add(new RedeployPublisher("",repo.toURI().toString(),false, false));

        MavenModuleSetBuild b = m3.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.SUCCESS, b);

        assertTrue( MavenUtil.maven3orLater( b.getMavenVersionUsed() ) );
        File artifactDir = new File(repo,"test/test/0.1-SNAPSHOT/");
        String[] files = artifactDir.list( new FilenameFilter()
        {
            
            public boolean accept( File dir, String name )
            {
                return name.endsWith( "tar.gz" );
            }
        });
        assertFalse("tar.gz doesn't exist",new File(repo,"test/test/0.1-SNAPSHOT/test-0.1-SNAPSHOT-bin.tar.gz").exists());
        assertTrue("tar.gz doesn't exist",!files[0].contains( "SNAPSHOT" ));
    }    
    
    @Test
    public void testTarGzUniqueVersionTrueMaven3() throws Exception {
        MavenModuleSet m3 = j.createMavenProject();
        MavenInstallation mvn = j.configureMaven3();
        m3.setMaven( mvn.getName() );        
        File repo = tmp.getRoot();
        // a fake build
        m3.setScm(new SingleFileSCM("pom.xml",getClass().getResource("targz-artifact.pom")));
        m3.getPublishersList().add(new RedeployPublisher("",repo.toURI().toString(),true, false));

        MavenModuleSetBuild b = m3.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.SUCCESS, b);
        
        assertTrue( MavenUtil.maven3orLater( b.getMavenVersionUsed() ) );
        
        File artifactDir = new File(repo,"test/test/0.1-SNAPSHOT/");
        String[] files = artifactDir.list( new FilenameFilter()
        {
            
            public boolean accept( File dir, String name )
            {
                return name.contains( "-bin.tar.gz" ) || name.endsWith( ".jar" ) || name.endsWith( "-bin.zip" );
            }
        });
        System.out.println("deployed files " + Arrays.asList( files ));
        assertFalse("tar.gz doesn't exist",new File(repo,"test/test/0.1-SNAPSHOT/test-0.1-SNAPSHOT-bin.tar.gz").exists());
        assertTrue("tar.gz doesn't exist",!files[0].contains( "SNAPSHOT" ));
        for (String file : files) {
            if (file.endsWith( "-bin.tar.gz" )) {
                String ver = StringUtils.remove( file, "-bin.tar.gz" );
                ver = ver.substring( ver.length() - 1, ver.length() );
                assertEquals("-bin.tar.gz not ended with 1 , file " + file , "1", ver);
            }
            if (file.endsWith( ".jar" )) {
                String ver = StringUtils.remove( file, ".jar" );
                ver = ver.substring( ver.length() - 1, ver.length() );
                assertEquals(".jar not ended with 1 , file " + file , "1", ver);
            }            
            if (file.endsWith( "-bin.zip" )) {
                String ver = StringUtils.remove( file, "-bin.zip" );
                ver = ver.substring( ver.length() - 1, ver.length() );
                assertEquals("-bin.zip not ended with 1 , file " + file , "1", ver);
            }            
        }
    }    

    @Bug(3773)
    @Test
    public void testDeployUnstable() throws Exception {
        j.configureDefaultMaven();
        MavenModuleSet m2 = j.createMavenProject();
        File repo = tmp.getRoot();
        // a build with a failing unit tests
        m2.setScm(new ExtractResourceSCM(getClass().getResource("maven-test-failure-findbugs.zip")));
        m2.getPublishersList().add(new RedeployPublisher("",repo.toURI().toString(),false, true));

        MavenModuleSetBuild b = m2.scheduleBuild2(0).get();
        j.assertBuildStatus(Result.UNSTABLE, b);

        assertTrue("Artifact should have been published even when the build is unstable",
                   new File(repo,"test/test/1.0-SNAPSHOT/test-1.0-SNAPSHOT.jar").exists());
    }
}
