/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.jvnet.hudson.test.LogRecorder.recorded;

import hudson.XmlFile;
import java.io.File;
import java.util.logging.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

class RunActionTest {

    @RegisterExtension
    private final JenkinsSessionExtension sessions = new JenkinsSessionExtension();

    @Issue("JENKINS-45892")
    @Test
    void badSerialization() throws Throwable {
        sessions.then(j -> {
                FreeStyleProject p = j.createFreeStyleProject("p");
                FreeStyleBuild b1 = j.buildAndAssertSuccess(p);
                FreeStyleBuild b2 = j.buildAndAssertSuccess(p);
                b2.addAction(new BadAction(b1));
                b2.save();
                String text = new XmlFile(new File(b2.getRootDir(), "build.xml")).asString();
                assertThat(text, not(containsString("<owner class=\"build\">")));
                assertThat(text, containsString("<id>p#1</id>"));
        });
        sessions.then(j -> {
            FreeStyleProject p = j.jenkins.getItemByFullName("p", FreeStyleProject.class);
            try (LogRecorder recorder = new LogRecorder()) {
                recorder.record(RunMap.class, Level.WARNING);
                recorder.capture(5);
                assertNull(p.getBuildByNumber(2));
                assertThat(recorder, recorded(
                        Level.WARNING,
                        containsString("could not load"),
                        hasProperty("cause",
                                hasProperty("message",
                                        containsString("Refusing to unmarshal PersistenceRoot subtype 'hudson.model.FreeStyleBuild' into field 'owner' in 'hudson.model.RunActionTest$BadAction'. " +
                                                "PersistenceRoot objects are document roots and must not appear as nested field values.")))));
            }
        });
    }

    static class BadAction extends InvisibleAction {
        final Run<?, ?> owner; // oops, should have been transient and used RunAction2

        BadAction(Run<?, ?> owner) {
            this.owner = owner;
        }
    }

}
