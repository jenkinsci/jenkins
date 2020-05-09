/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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

import hudson.model.Node;
import org.apache.tools.ant.DirectoryScanner;
import static org.hamcrest.Matchers.*;
import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.*;
import org.junit.Rule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;

public class FilePathTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-50237")
    @Test
    public void listGlob() throws Exception {
        for (Node n : new Node[] {r.jenkins, r.createOnlineSlave()}) {
            FilePath d = n.getRootPath().child("globbing");
            FilePath exists = d.child("dir/exists");
            exists.write("", null);
            assertThat(d.list("**/exists"), arrayContainingInAnyOrder(exists));
            assertThat(d.list("**/nonexistent"), emptyArray());
            FilePath nonexistentDir = d.child("nonexistentDir");
            try {
                assertThat("if it works at all, should be empty", nonexistentDir.list("**"), emptyArray());
            } catch (Exception x) {
                assertThat(x.toString(), containsString(nonexistentDir.getRemote() + DirectoryScanner.DOES_NOT_EXIST_POSTFIX));
            }
        }
    }

    @Test
    @Issue("JENKINS-32778")
    @LocalData("zip_with_relative")
    public void zipRelativePathHandledCorrectly() throws Exception {
        assumeTrue(Functions.isWindows());
        FilePath zipFile = r.jenkins.getRootPath().child("zip-with-folder.zip");
        FilePath targetLocation = r.jenkins.getRootPath().child("unzip-target");

        FilePath simple1 = targetLocation.child("simple1.txt");
        FilePath simple2 = targetLocation.child("child").child("simple2.txt");

        assertThat(simple1.exists(), is(false));
        assertThat(simple2.exists(), is(false));

        zipFile.unzip(targetLocation);

        assertThat(simple1.exists(), is(true));
        assertThat(simple2.exists(), is(true));
    }

    @Test
    @Issue("JENKINS-32778")
    @LocalData("zip_with_relative")
    public void zipAbsolutePathHandledCorrectly_win() throws Exception {
        assumeTrue(Functions.isWindows());

        // this special zip contains a ..\..\ [..] \..\Temp\evil.txt
        FilePath zipFile = r.jenkins.getRootPath().child("zip-slip-win.zip");

        FilePath targetLocation = r.jenkins.getRootPath().child("unzip-target");
 
        FilePath good = targetLocation.child("good.txt");

        assertThat(good.exists(), is(false));
        
        try {
            zipFile.unzip(targetLocation);
            fail("The evil.txt should have triggered an exception");
        }
        catch(IOException e){
            assertThat(e.getMessage(), containsString("contains illegal file name that breaks out of the target directory"));
        }

        // as the unzip operation failed, the good.txt was potentially unzipped
        // but we need to make sure that the evil.txt is not there
        File evil = new File(r.jenkins.getRootDir(), "..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\Temp\\evil.txt");
        assertThat(evil.exists(), is(false));
    }

    @Test
    @Issue("JENKINS-32778")
    @LocalData("zip_with_relative")
    public void zipAbsolutePathHandledCorrectly_unix() throws Exception {
        assumeFalse(Functions.isWindows());

        // this special zip contains a ../../../ [..] /../tmp/evil.txt
        FilePath zipFile = r.jenkins.getRootPath().child("zip-slip.zip");

        FilePath targetLocation = r.jenkins.getRootPath().child("unzip-target");

        FilePath good = targetLocation.child("good.txt");

        assertThat(good.exists(), is(false));

        try {
            zipFile.unzip(targetLocation);
            fail("The evil.txt should have triggered an exception");
        }
        catch(IOException e){
            assertThat(e.getMessage(), containsString("contains illegal file name that breaks out of the target directory"));
        }

        // as the unzip operation failed, the good.txt was potentially unzipped
        // but we need to make sure that the evil.txt is not there
        File evil = new File(r.jenkins.getRootDir(), "../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../tmp/evil.txt");
        assertThat(evil.exists(), is(false));
    }

    @Test
    @Issue("JENKINS-32778")
    @LocalData("zip_with_relative")
    public void zipRelativePathHandledCorrectly_oneUp() throws Exception {
        // internal structure:
        //  ../simple3.txt
        //  child/simple2.txt
        //  simple1.txt
        FilePath zipFile = r.jenkins.getRootPath().child("zip-rel-one-up.zip");
        FilePath targetLocation = r.jenkins.getRootPath().child("unzip-target");

        FilePath simple3 = targetLocation.getParent().child("simple3.txt");

        assertThat(simple3.exists(), is(false));

        try {
            zipFile.unzip(targetLocation);
            fail("The ../simple3.txt should have triggered an exception");
        }
        catch(IOException e){
            assertThat(e.getMessage(), containsString("contains illegal file name that breaks out of the target directory"));
        }

        assertThat(simple3.exists(), is(false));
    }
    
    @Test
    @Issue("XXX")
    @LocalData("zip_with_relative")
    public void zipTarget_regular() throws Exception {
        assumeTrue(Functions.isWindows());
        File zipFile = new File(r.jenkins.getRootDir(), "zip-with-folder.zip");
        File targetLocation = new File(r.jenkins.getRootDir(), "unzip-target");
        FilePath targetLocationFP = r.jenkins.getRootPath().child("unzip-target");

        FilePath simple1 = targetLocationFP.child("simple1.txt");
        FilePath simple2 = targetLocationFP.child("child").child("simple2.txt");

        assertThat(simple1.exists(), is(false));
        assertThat(simple2.exists(), is(false));

        Method unzipPrivateMethod;
        unzipPrivateMethod = FilePath.class.getDeclaredMethod("unzip", File.class, File.class);
        unzipPrivateMethod.setAccessible(true);

        FilePath fp = new FilePath(new File("."));
        unzipPrivateMethod.invoke(fp, targetLocation, zipFile);

        assertThat(simple1.exists(), is(true));
        assertThat(simple2.exists(), is(true));
    }

    @Test
    @Issue("XXX")
    @LocalData("zip_with_relative")
    public void zipTarget_relative() throws Exception {
        assumeTrue(Functions.isWindows());
        File zipFile = new File(r.jenkins.getRootDir(), "zip-with-folder.zip");
        // the main difference is here, the ./
        File targetLocation = new File(r.jenkins.getRootDir(), "./unzip-target");
        FilePath targetLocationFP = r.jenkins.getRootPath().child("unzip-target");

        FilePath simple1 = targetLocationFP.child("simple1.txt");
        FilePath simple2 = targetLocationFP.child("child").child("simple2.txt");

        assertThat(simple1.exists(), is(false));
        assertThat(simple2.exists(), is(false));

        Method unzipPrivateMethod;
        unzipPrivateMethod = FilePath.class.getDeclaredMethod("unzip", File.class, File.class);
        unzipPrivateMethod.setAccessible(true);

        FilePath fp = new FilePath(new File("."));
        unzipPrivateMethod.invoke(fp, targetLocation, zipFile);

        assertThat(simple1.exists(), is(true));
        assertThat(simple2.exists(), is(true));
    }
}
