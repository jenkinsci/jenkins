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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Tests of {@link GlobalSCMRetryCountConfiguration}.
 * @author Panagiotis Galatsanos
 */
public class GlobalSCMRetryCountConfigurationTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-36387")
    public void shouldExceptOnNullScmRetryCount() throws Exception {
        try {
            JSONObject json = new JSONObject();
            j.getInstance().setScmCheckoutRetryCount(5);
            assertThat("Wrong value, it should be equal to 5",
                    j.getInstance().getScmCheckoutRetryCount(), equalTo(5));

            j.getInstance().setScmCheckoutRetryCount(3);
            assertThat("Wrong value, it should be equal to 3",
                    j.getInstance().getScmCheckoutRetryCount(), equalTo(3));

            j.jenkins.setScmCheckoutRetryCount(json.getInt(null));
        } catch (JSONException e) {
            assertThat("Scm Retry count value changed! This shouldn't happen.",
                    j.getInstance().getScmCheckoutRetryCount(), equalTo(3));
        }
    }
}
