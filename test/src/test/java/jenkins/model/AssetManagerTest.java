/*
 * The MIT License
 *
 * Copyright (c) 2019 CloudBees, Inc.
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

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.Assert.assertEquals;

public class AssetManagerTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @Issue("JENKINS-58736")
    public void emptyAssetDoesNotThrowError() throws Exception {
        URL url = new URL(j.getURL() + "assets");
        HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
        assertEquals(HttpURLConnection.HTTP_NOT_FOUND, httpCon.getResponseCode());
    }

    @Test
    @Issue("JENKINS-9598")
    public void jqueryLoad() throws Exception {
        // webclient does not work because it tries to parse the jquery2.js and there is a missing comma
        URL url = new URL(j.getURL() + "assets/jquery-detached/jsmodules/jquery2.js");
        HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
        assertEquals(HttpURLConnection.HTTP_OK, httpCon.getResponseCode());
    }
}
