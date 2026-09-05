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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyArray;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import hudson.model.Node;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import org.apache.tools.ant.DirectoryScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

@WithJenkins
class FilePathTest {

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
    }

    @Issue("JENKINS-50237")
    @Test
    void listGlob() throws Exception {
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
    void zipRelativePathHandledCorrectly() throws Exception {
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
    void zipAbsolutePathHandledCorrectly_win() throws Exception {
        assumeTrue(Functions.isWindows());

        // this special zip contains a ..\..\ [..] \..\Temp\evil.txt
        FilePath zipFile = r.jenkins.getRootPath().child("zip-slip-win.zip");

        FilePath targetLocation = r.jenkins.getRootPath().child("unzip-target");

        FilePath good = targetLocation.child("good.txt");

        assertThat(good.exists(), is(false));

        IOException e = assertThrows(IOException.class, () -> zipFile.unzip(targetLocation));
        assertThat(e.getMessage(), containsString("contains illegal file name that breaks out of the target directory"));

        // as the unzip operation failed, the good.txt was potentially unzipped
        // but we need to make sure that the evil.txt is not there
        File evil = new File(r.jenkins.getRootDir(), "..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\..\\Temp\\evil.txt");
        assertThat(evil.exists(), is(false));
    }

    @Test
    @Issue("JENKINS-32778")
    @LocalData("zip_with_relative")
    void zipAbsolutePathHandledCorrectly_unix() throws Exception {
        assumeFalse(Functions.isWindows());

        // this special zip contains a ../../../ [..] /../tmp/evil.txt
        FilePath zipFile = r.jenkins.getRootPath().child("zip-slip.zip");

        FilePath targetLocation = r.jenkins.getRootPath().child("unzip-target");

        FilePath good = targetLocation.child("good.txt");

        assertThat(good.exists(), is(false));

        IOException e = assertThrows(IOException.class, () -> zipFile.unzip(targetLocation));
        assertThat(e.getMessage(), containsString("contains illegal file name that breaks out of the target directory"));

        // as the unzip operation failed, the good.txt was potentially unzipped
        // but we need to make sure that the evil.txt is not there
        File evil = new File(r.jenkins.getRootDir(), "../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../tmp/evil.txt");
        assertThat(evil.exists(), is(false));
    }

    @Test
    @Issue("JENKINS-32778")
    @LocalData("zip_with_relative")
    void zipRelativePathHandledCorrectly_oneUp() throws Exception {
        // internal structure:
        //  ../simple3.txt
        //  child/simple2.txt
        //  simple1.txt
        FilePath zipFile = r.jenkins.getRootPath().child("zip-rel-one-up.zip");
        FilePath targetLocation = r.jenkins.getRootPath().child("unzip-target");

        FilePath simple3 = targetLocation.getParent().child("simple3.txt");

        assertThat(simple3.exists(), is(false));

        IOException e = assertThrows(IOException.class, () -> zipFile.unzip(targetLocation));
        assertThat(e.getMessage(), containsString("contains illegal file name that breaks out of the target directory"));

        assertThat(simple3.exists(), is(false));
    }

    @Test
    @Issue("XXX")
    @LocalData("zip_with_relative")
    void zipTarget_regular() throws Exception {
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
    void zipTarget_relative() throws Exception {
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

    @Test
    @Issue("JENKINS-66094")
    @LocalData("ZipSlipSamePathPrefix")
    void zipSlipSamePathPrefix() throws Exception {
        assumeFalse(Functions.isWindows());

        // > unzip -l evil.zip
        // good.txt
        //  ../foo_evil.txt
        FilePath zipFile = r.jenkins.getRootPath().child("evil.zip");

        // foo_evil.txt will be extracted to unzip-target/foo_evil.txt
        // which has the same path prefix as unzip-target/foo
        FilePath targetLocationParent = r.jenkins.getRootPath().child("unzip-target");
        FilePath targetLocationFoo = targetLocationParent.child("foo");
        FilePath evilEntry = targetLocationParent.child("foo_evil.txt");

        assertThat(evilEntry.exists(), is(false));

        IOException e = assertThrows(IOException.class, () -> zipFile.unzip(targetLocationFoo));
        assertThat(e.getMessage(), containsString("contains illegal file name that breaks out of the target directory"));

        assertThat(evilEntry.exists(), is(false));
    }

    @Test
    @Issue("JENKINS-66094")
    @LocalData("ZipSlipSamePathPrefix")
    void zipSlipSamePathPrefixWin() throws Exception {
        assumeTrue(Functions.isWindows());

        // > unzip -l evil-win.zip
        // good.txt
        //  ..\foo_evil.txt
        FilePath zipFile = r.jenkins.getRootPath().child("evil-win.zip");

        // foo_evil.txt will be extracted to unzip-target\foo_evil.txt
        // which has the same path prefix as unzip-target\foo
        FilePath targetLocationParent = r.jenkins.getRootPath().child("unzip-target");
        FilePath targetLocationFoo = targetLocationParent.child("foo");
        FilePath evilEntry = targetLocationParent.child("foo_evil.txt");

        assertThat(evilEntry.exists(), is(false));

        IOException e = assertThrows(IOException.class, () -> zipFile.unzip(targetLocationFoo));
        assertThat(e.getMessage(), containsString("contains illegal file name that breaks out of the target directory"));

        assertThat(evilEntry.exists(), is(false));
    }
}
