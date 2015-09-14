/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
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

import net.sf.json.JSONObject;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;

import java.io.IOException;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class UpdateCenterConnectionStatusTest {
    
    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void doConnectionStatus_default_site() throws IOException, SAXException {
        JSONObject response = jenkinsRule.getJSON("updateCenter/connectionStatus");

        Assert.assertEquals("ok", response.getString("status"));
        JSONObject statusObj = response.getJSONObject("data");
        Assert.assertTrue(statusObj.has("updatesite"));
        Assert.assertTrue(statusObj.has("internet"));

        // The following is equivalent to the above
        response = jenkinsRule.getJSON("updateCenter/connectionStatus?siteId=default");

        Assert.assertEquals("ok", response.getString("status"));
        statusObj = response.getJSONObject("data");
        Assert.assertTrue(statusObj.has("updatesite"));
        Assert.assertTrue(statusObj.has("internet"));
    } 

    @Test
    public void doConnectionStatus_unknown_site() throws IOException, SAXException {
        JSONObject response = jenkinsRule.getJSON("updateCenter/connectionStatus?siteId=blahblah");

        Assert.assertEquals("error", response.getString("status"));
        Assert.assertEquals("Unknown site 'blahblah'.", response.getString("message"));
    } 
}
