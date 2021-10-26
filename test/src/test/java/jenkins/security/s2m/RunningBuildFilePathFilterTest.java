/*
 * The MIT License
 *
 * Copyright 2021 CloudBees, Inc.
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

package jenkins.security.s2m;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import jenkins.security.MasterToSlaveCallable;
import org.apache.commons.io.FileUtils;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

@Issue("SECURITY-2428")
public class RunningBuildFilePathFilterTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void accessPermittedOnlyFromCurrentBuild() throws Exception {
        ExtensionList.lookupSingleton(AdminWhitelistRule.class).setMasterKillSwitch(false);
        FreeStyleProject main = r.createFreeStyleProject("main");
        main.setAssignedNode(r.createSlave());
        WriteBackPublisher wbp = new WriteBackPublisher();
        main.getBuildersList().add(wbp);
        // Normal case: writing to our own build directory
        wbp.controllerFile = build -> new File(build.getRootDir(), "stuff.txt");
        r.buildAndAssertSuccess(main);
        // Attacks:
        wbp.legal = false;
        // Writing to someone else’s build directory (covered by RunningBuildFilePathFilter)
        FreeStyleProject other = r.createFreeStyleProject("other");
        r.buildAndAssertSuccess(other);
        wbp.controllerFile = build -> new File(other.getBuildByNumber(1).getRootDir(), "hack");
        r.buildAndAssertSuccess(main);
        // Writing to some other directory (covered by AdminWhitelistRule)
        wbp.controllerFile = build -> new File(r.jenkins.getRootDir(), "hack");
        r.buildAndAssertSuccess(main);
        // Writing to a sensitive file even in my own build dir (covered by AdminWhitelistRule)
        wbp.controllerFile = build -> new File(build.getRootDir(), "build.xml");
        r.buildAndAssertSuccess(main);
        // Writing to the directory of an earlier build
        wbp.controllerFile = build -> new File(main.getBuildByNumber(1).getRootDir(), "stuff.txt");
        r.buildAndAssertSuccess(main);

        System.setProperty(RunningBuildFilePathFilter.class.getName() + ".FAIL", "false");
        try {
            wbp.legal = true;
            wbp.controllerFile = build -> new File(main.getBuildByNumber(1).getRootDir(), "stuff.txt");
            r.buildAndAssertSuccess(main);
        } finally {
            System.clearProperty(RunningBuildFilePathFilter.class.getName() + ".FAIL");
        }
    }

    private static final class WriteBackPublisher extends TestBuilder {
        Function<AbstractBuild<?, ?>, File> controllerFile;
        boolean legal = true;
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            File f = controllerFile.apply(build);
            listener.getLogger().println("Will try to write to " + f + "; legal? " + legal);
            String text = build.getExternalizableId();
            try {
                launcher.getChannel().call(new WriteBackCallable(new FilePath(f), text));
                if (legal) {
                    assertEquals(text, FileUtils.readFileToString(f, StandardCharsets.UTF_8));
                    listener.getLogger().println("Allowed as expected");
                } else {
                    fail("should not have been allowed");
                }
            } catch (Exception x) {
                if (!legal && x.toString().contains("SecurityException")) {
                    // TODO assert error message is either from RunningBuildFilePathFilter or from SoloFilePathFilter
                    Functions.printStackTrace(x, listener.error("Rejected as expected!"));
                } else {
                    throw x;
                }
            }
            return true;
        }
    }

    private static final class WriteBackCallable extends MasterToSlaveCallable<Void, IOException> {
        private final FilePath controllerFile;
        private final String text;
        WriteBackCallable(FilePath controllerFile, String text) {
            this.controllerFile = controllerFile;
            this.text = text;
        }
        @Override
        public Void call() throws IOException {
            assertTrue(controllerFile.isRemote());
            try {
                controllerFile.write(text, null);
            } catch (InterruptedException x) {
                throw new IOException(x);
            }
            return null;
        }

    }

}
