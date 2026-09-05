/*
 * The MIT License
 *
 * Copyright 2013 Jesse Glick.
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ViewJobTest {

    private JenkinsRule rule;

    @BeforeEach
    void setUp(JenkinsRule j) {
        rule = j;
    }

    @Issue("JENKINS-19377")
    @Test
    void removeRun() throws Exception {
        J j = rule.jenkins.createProject(J.class, "j");
        R r1 = j.nue();
        R r2 = j.nue();
        assertEquals("[2, 1]", j.getBuildsAsMap().keySet().toString());
        j.removeRun(r1);
        assertEquals("[2]", j.getBuildsAsMap().keySet().toString());
    }

    @SuppressWarnings({"rawtypes", "deprecation"})
    public static final class J extends ViewJob<J, R> implements TopLevelItem {

        J(ItemGroup parent, String name) {
            super(parent, name);
        }

        @Override protected void reload() {
            runs.load(this, new RunMap.Constructor<>() {
                @Override
                public R create(File dir) throws IOException {
                    return new R(J.this, dir);
                }

                @Override
                public Class<R> getBuildClass() {
                    return R.class;
                }
            });
        }

        @Override public TopLevelItemDescriptor getDescriptor() {
            return Jenkins.get().getDescriptorByType(DescriptorImpl.class);
        }

        @TestExtension public static final class DescriptorImpl extends TopLevelItemDescriptor {

            @Override public TopLevelItem newInstance(ItemGroup parent, String name) {
                return new J(parent, name);
            }

        }

        R nue() throws IOException {
            R r = new R(this);
            _getRuns();
            runs.put(r);
            return r;
        }

    }

    public static final class R extends Run<J, R> {

        @SuppressWarnings("checkstyle:redundantmodifier")
        public R(J j) throws IOException {
            super(j);
        }

        @SuppressWarnings("checkstyle:redundantmodifier")
        public R(J j, File d) throws IOException {
            super(j, d);
        }

    }

}
