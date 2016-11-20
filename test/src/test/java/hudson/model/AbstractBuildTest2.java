/*
 * The MIT License
 *
 * Copyright (c) 2015 CloudBees, Inc.
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

import hudson.Launcher;
import hudson.model.queue.QueueTaskFuture;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

/**
 * Unit tests of {@link AbstractBuild}.
 * @author Oleg Nenashev
 */
public class AbstractBuildTest2 {
    
    @Rule
    public JenkinsRule rule = new JenkinsRule();
    
    @Test
    @Issue("JENKINS-30730")
    @SuppressWarnings("deprecation")
    public void reportErrorShouldNotFailForNonPublisherClass() throws Exception {
        FreeStyleProject prj = rule.createFreeStyleProject();
        ErrorneousJobProperty errorneousJobProperty = new ErrorneousJobProperty();
        prj.addProperty(errorneousJobProperty);
        QueueTaskFuture<FreeStyleBuild> future = prj.scheduleBuild2(0);     
        assertThat("Build should be actually scheduled by Jenkins", future, notNullValue());
        FreeStyleBuild build = future.get();
        rule.assertLogContains(ErrorneousJobProperty.ERROR_MESSAGE, build);
        rule.assertLogNotContains(ClassCastException.class.getName(), build);
    }
    
    /**
     * Job property, which always fails with an exception.
     */
    public static class ErrorneousJobProperty extends JobProperty<FreeStyleProject> {

        public static final String ERROR_MESSAGE = "This publisher fails by design";
        
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
            throw new IOException(ERROR_MESSAGE);
        }
        
        @TestExtension("reportErrorShouldNotFailForNonPublisherClass")
        public static class DescriptorImpl extends JobPropertyDescriptor {

            @Override
            public String getDisplayName() {
                return "Always throws exception in perform()";
            }  
        }
    }
}
