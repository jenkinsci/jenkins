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
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

import hudson.XmlFile;
import java.util.logging.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

class AbstractItem2Test {

    @RegisterExtension
    private final JenkinsSessionExtension sessions = new JenkinsSessionExtension();

    private final LogRecorder logging = new LogRecorder().record(XmlFile.class, Level.WARNING).capture(100);

    @Issue("JENKINS-45892")
    @Test
    void badSerialization() throws Throwable {
        sessions.then(j -> {
                FreeStyleProject p1 = j.createFreeStyleProject("p1");
                p1.setDescription("this is p1");
                FreeStyleProject p2 = j.createFreeStyleProject("p2");
                p2.addProperty(new BadProperty(p1));
                String text = p2.getConfigFile().asString();
                assertThat(text, not(containsString("<description>this is p1</description>")));
                assertThat(text, containsString("<fullName>p1</fullName>"));
                assertThat(logging.getMessages().toString(), containsString(p1.toString()));
                assertThat(logging.getMessages().toString(), containsString(p2.getConfigFile().toString()));
        });
        sessions.then(j -> {
                FreeStyleProject p1 = j.jenkins.getItemByFullName("p1", FreeStyleProject.class);
                FreeStyleProject p2 = j.jenkins.getItemByFullName("p2", FreeStyleProject.class);
                /*
                 * AbstractItem.Replacer.readResolve() is racy, as its comments acknowledge. Jobs
                 * are loaded in parallel, and p1 may not have been loaded yet when we are loading
                 * p2. The only way for this test to work reliably is to reload p2 after p1 has
                 * already been loaded, thus assuring that p2's reference to p1 can be properly
                 * deserialized.
                 */
                p2.doReload();
                assertEquals(p1, p2.getProperty(BadProperty.class).other);
        });
    }

    static class BadProperty extends JobProperty<FreeStyleProject> {
        final FreeStyleProject other;

        BadProperty(FreeStyleProject other) {
            this.other = other;
        }
    }

}
