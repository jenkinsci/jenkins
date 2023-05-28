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

package hudson.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNotNull;

import hudson.model.UpdateCenter.DownloadJob;
import hudson.model.UpdateCenter.DownloadJob.Failure;
import hudson.model.UpdateCenter.DownloadJob.Success;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

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
    @Ignore("TODO randomly fails: SocketTimeoutException from goTo due to GET http://localhost:…/update-center.json?…; or: Downloaded file …/changelog-history.jpi.tmp does not match expected SHA-256, expected '…', actual '…'")
    @Test public void install() throws Exception {
        UpdateSite.neverUpdate = false;
        j.jenkins.pluginManager.doCheckUpdatesServer(); // load the metadata
        UpdateSite.Plugin plugin = j.jenkins.getUpdateCenter().getPlugin("changelog-history");
        assumeNotNull(plugin);
        DownloadJob job = (DownloadJob) plugin.deploy().get(); // this seems like one of the smallest plugin
        System.out.println(job.status);
        assertThat(job.status, instanceOf(Success.class));
    }

    @Test public void getLastUpdatedString() {
        UpdateSite.neverUpdate = false;
        assertTrue(j.jenkins.getUpdateCenter().getById("default").isDue());
        assertEquals(Messages.UpdateCenter_n_a(), j.jenkins.getUpdateCenter().getLastUpdatedString());
    }

    @Ignore("TODO times out when UC is down with SocketTimeoutException")
    @Issue("SECURITY-234")
    @Test public void installInvalidChecksum() throws Exception {
        UpdateSite.neverUpdate = false;
        j.jenkins.pluginManager.doCheckUpdatesServer(); // load the metadata
        String wrongChecksum = "ABCDEFG1234567890";

        // usually the problem is the file having a wrong checksum, but changing the expected one works just the same
        UpdateSite.Plugin plugin = j.jenkins.getUpdateCenter().getSite("default").getPlugin("changelog-history");
        assumeNotNull(plugin);
        plugin.sha512 = wrongChecksum;
        DownloadJob job = (DownloadJob) plugin.deploy().get();
        assertThat(job.status, instanceOf(Failure.class));
        assertTrue("error message references checksum", ((Failure) job.status).problem.getMessage().contains(wrongChecksum));
    }

}
