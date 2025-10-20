/*
 * The MIT License
 *
 * Copyright (c) 2016
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import hudson.model.Descriptor;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.Stapler;

/**
 * Tests of {@link GlobalSCMRetryCountConfiguration}.
 * @author Panagiotis Galatsanos
 */
@WithJenkins
class GlobalSCMRetryCountConfigurationTest {

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    @Issue("JENKINS-36387")
    void shouldExceptOnNullScmRetryCount() {
        try {
            JSONObject json = new JSONObject();
            GlobalSCMRetryCountConfiguration gc = (GlobalSCMRetryCountConfiguration)
                    GlobalConfiguration.all().getDynamic("jenkins.model.GlobalSCMRetryCountConfiguration");

            json.element("scmCheckoutRetryCount", 5);
            gc.configure(Stapler.getCurrentRequest2(), json);
            assertThat("Wrong value, it should be equal to 5",
                    j.getInstance().getScmCheckoutRetryCount(), equalTo(5));

            json.element("scmCheckoutRetryCount", 3);
            gc.configure(Stapler.getCurrentRequest2(), json);
            assertThat("Wrong value, it should be equal to 3",
                    j.getInstance().getScmCheckoutRetryCount(), equalTo(3));

            JSONObject emptyJson = new JSONObject();
            gc.configure(Stapler.getCurrentRequest2(), emptyJson);
        } catch (Descriptor.FormException e) {
            assertThat("Scm Retry count value changed! This shouldn't happen.",
                    j.getInstance().getScmCheckoutRetryCount(), equalTo(3));
        }
    }
}
