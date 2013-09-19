/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
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
package hudson.model

import org.jvnet.hudson.test.JenkinsRule
import org.junit.Rule
import org.junit.Test
import static org.junit.Assert.*
import org.junit.Assume
import hudson.model.UpdateCenter.DownloadJob.Success
import hudson.model.UpdateSite

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class UpdateCenter2Test {

    @Rule public JenkinsRule j = new JenkinsRule();

    /**
     * Makes sure a plugin installs fine.
     */
    @Test void install() {
        Assume.assumeFalse("SocketTimeoutException from goTo due to GET http://localhost:…/update-center.json?…", "https://jenkins.ci.cloudbees.com/job/core/job/jenkins_main_trunk/".equals(System.getenv("JOB_URL")))
        UpdateSite.neverUpdate = false;
        j.createWebClient().goTo("") // load the metadata
        def job = j.jenkins.updateCenter.getPlugin("changelog-history").deploy().get(); // this seems like one of the smallest plugin
        println job.status;
        assertTrue(job.status instanceof Success)
    }

    @Test void getLastUpdatedString() {
        UpdateSite.neverUpdate = false
        assertTrue(j.jenkins.updateCenter.getById("default").due)
        assertEquals(hudson.model.Messages.UpdateCenter_n_a(), j.jenkins.updateCenter.lastUpdatedString)
    }

}
