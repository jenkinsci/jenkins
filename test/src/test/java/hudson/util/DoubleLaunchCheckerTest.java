/*
 * The MIT License
 *
 * Copyright 2023 CloudBees, Inc.
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

package hudson.util;

import static org.awaitility.Awaitility.await;

import hudson.ExtensionList;
import java.time.Duration;
import java.util.logging.Level;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.PrefixedOutputStream;
import org.jvnet.hudson.test.RealJenkinsRule;

public final class DoubleLaunchCheckerTest {

    @Rule
    public RealJenkinsRule mainController = new RealJenkinsRule().
        withName("main").
        withColor(PrefixedOutputStream.Color.BLUE).
        withLogger(DoubleLaunchChecker.class, Level.FINE);

    @Rule
    public RealJenkinsRule duplicateController = new RealJenkinsRule(mainController).
        withName("dupe").
        withColor(PrefixedOutputStream.Color.RED).
        withLogger(DoubleLaunchChecker.class, Level.FINE);

    @Test
    public void activated() throws Throwable {
        mainController.startJenkins();
        duplicateController.startJenkins();
        mainController.runRemotely(DoubleLaunchCheckerTest::waitForWarning);
    }

    private static void waitForWarning(JenkinsRule r) throws Throwable {
        await().atMost(Duration.ofMinutes(3)).until(ExtensionList.lookupSingleton(DoubleLaunchChecker.class)::isActivated);
    }

}
