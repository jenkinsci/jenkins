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

import org.jvnet.hudson.test.HudsonTestCase
import hudson.model.UpdateCenter.DownloadJob.Success

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class UpdateCenter2Test extends HudsonTestCase {
    /**
     * Makes sure a plugin installs fine.
     */
    void testInstall() {
        UpdateSite.neverUpdate = false;
        createWebClient().goTo("/") // load the metadata
        def job = jenkins.updateCenter.getPlugin("changelog-history").deploy().get(); // this seems like one of the smallest plugin
        println job.status;
        assertTrue(job.status instanceof Success)
    }
}
