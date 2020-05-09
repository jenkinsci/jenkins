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

import hudson.XmlFile;
import java.util.logging.Level;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import org.junit.Rule;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class AbstractItem2Test {

    @Rule
    public RestartableJenkinsRule rr = new RestartableJenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule().record(XmlFile.class, Level.WARNING).capture(100);

    @Issue("JENKINS-45892")
    @Test
    public void badSerialization() {
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                FreeStyleProject p1 = rr.j.createFreeStyleProject("p1");
                p1.setDescription("this is p1");
                FreeStyleProject p2 = rr.j.createFreeStyleProject("p2");
                p2.addProperty(new BadProperty(p1));
                String text = p2.getConfigFile().asString();
                assertThat(text, not(containsString("<description>this is p1</description>")));
                assertThat(text, containsString("<fullName>p1</fullName>"));
                assertThat(logging.getMessages().toString(), containsString(p1.toString()));
                assertThat(logging.getMessages().toString(), containsString(p2.getConfigFile().toString()));
            }
        });
        rr.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                FreeStyleProject p1 = rr.j.jenkins.getItemByFullName("p1", FreeStyleProject.class);
                FreeStyleProject p2 = rr.j.jenkins.getItemByFullName("p2", FreeStyleProject.class);
                assertEquals(/* does not work yet: p1 */ null, p2.getProperty(BadProperty.class).other);
            }
        });
    }
    static class BadProperty extends JobProperty<FreeStyleProject> {
        final FreeStyleProject other;
        BadProperty(FreeStyleProject other) {
            this.other = other;
        }
    }

}
