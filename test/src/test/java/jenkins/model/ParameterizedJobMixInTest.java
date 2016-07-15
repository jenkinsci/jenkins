/*
 * The MIT License
 *
 * Copyright (c) 2016 Oleg Nenashev.
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
package jenkins.model;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Tests of {@link ParameterizedJobMixIn}.
 * @author Oleg Nenashev
 */
public class ParameterizedJobMixInTest {
    
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Test
    public void doBuild_shouldFailWhenInvokingDisabledProject() throws Exception {
        final FreeStyleProject project = j.createFreeStyleProject();
        project.doDisable();
        
        final JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.assertFails(project.getUrl() + "build", HttpServletResponse.SC_CONFLICT);
    }
    
    @Test
    @Issue("JENKINS-36193")
    public void doBuildWithParameters_shouldFailWhenInvokingDisabledProject() throws Exception {
        final FreeStyleProject project = j.createFreeStyleProject();
        project.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("FOO", "BAR")));
        project.doDisable();
        
        final JenkinsRule.WebClient webClient = j.createWebClient();
        webClient.assertFails(project.getUrl() + "buildWithParameters", HttpServletResponse.SC_CONFLICT);
    }
}
