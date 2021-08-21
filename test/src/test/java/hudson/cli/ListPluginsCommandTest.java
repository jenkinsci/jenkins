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
package hudson.cli;

import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.hamcrest.MatcherAssert.assertThat;

public class ListPluginsCommandTest {
    
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Test
    public void listPluginsExpectedUsage() throws Exception {
        assertNull(j.jenkins.getPluginManager().getPlugin("token-macro"));
        CLICommandInvoker.Result result = new CLICommandInvoker(j, new ListPluginsCommand())
                .invoke();
        assertThat(result, CLICommandInvoker.Matcher.succeeded());
        assertThat(result, CLICommandInvoker.Matcher.hasNoStandardOutput());
        assertThat(result.stdout(), not(containsString("token-macro")));
        
        assertThat(new CLICommandInvoker(j, new InstallPluginCommand()).
                        withStdin(ListPluginsCommandTest.class.getResourceAsStream("/plugins/token-macro.hpi")).
                        invokeWithArgs("-name", "token-macro", "-deploy", "="),
                CLICommandInvoker.Matcher.succeeded());
        assertNotNull(j.jenkins.getPluginManager().getPlugin("token-macro"));
        
        result = new CLICommandInvoker(j, new ListPluginsCommand())
                .invoke()
        ;
        assertThat(result, CLICommandInvoker.Matcher.succeeded());
        assertThat(result.stdout(), containsString("token-macro"));
    }
    
    @Test
    @Issue("SECURITY-771")
    public void onlyAccessibleForAdmin() throws Exception {
        CLICommandInvoker.Result result = new CLICommandInvoker(j, new ListPluginsCommand())
                .authorizedTo(Jenkins.READ)
                .invoke();
        assertThat(result, CLICommandInvoker.Matcher.failedWith(6 /* not authorized */));
        
        result = new CLICommandInvoker(j, new ListPluginsCommand())
                .authorizedTo(Jenkins.ADMINISTER)
                .invoke()
        ;
        assertThat(result, CLICommandInvoker.Matcher.succeeded());
    }
}
