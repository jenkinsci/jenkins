/*
 * The MIT License
 *
 * Copyright (c) 2017 Oleg Nenashev.
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

package hudson.views;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import hudson.model.Descriptor;
import net.sf.json.JSONObject;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.kohsuke.stapler.MockStaplerRequestBuilder;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Tests of {@link GlobalDefaultViewConfiguration}.
 * @author Oleg Nenashev
 */
public class GlobalDefaultViewConfigurationTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-42717")
    public void shouldNotFailIfTheDefaultViewIsMissing() {
        String viewName = "NonExistentView";
        GlobalDefaultViewConfiguration c = new GlobalDefaultViewConfiguration();

        StaplerRequest2 create = new MockStaplerRequestBuilder(j, "/configure").build();
        JSONObject params = new JSONObject();
        params.accumulate("primaryView", viewName);
        try {
            c.configure(create, params);
        } catch (Descriptor.FormException ex) {
            assertThat("Wrong exception message for the form failure",
                    ex.getMessage(), containsString(Messages.GlobalDefaultViewConfiguration_ViewDoesNotExist(viewName)));
            return;
        }
        Assert.fail("Expected FormException");
    }

}
