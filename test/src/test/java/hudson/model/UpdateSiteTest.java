/*
 * The MIT License
 *
 * Copyright 2012 Jesse Glick.
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

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequestSettings;
import hudson.model.UpdateSite.Data;
import hudson.util.PersistedList;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import org.apache.commons.io.IOUtils;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class UpdateSiteTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test public void relativeURLs() throws Exception {
        PersistedList<UpdateSite> sites = j.jenkins.getUpdateCenter().getSites();
        sites.clear();
        URL url = UpdateSiteTest.class.getResource("/plugins/tasks-update-center.json");
        UpdateSite site = new UpdateSite(UpdateCenter.ID_DEFAULT, url.toString());
        sites.add(site);
        UpdateSite.signatureCheck = false;
        { // XXX pending UpdateSite.updateDirectly:
            j.jenkins.setCrumbIssuer(null);
            WebRequestSettings wrs = new WebRequestSettings(new URL(j.getURL(), "/updateCenter/byId/default/postBack"), HttpMethod.POST);
            wrs.setRequestBody(IOUtils.toString(url.openStream()));
            Page p = j.createWebClient().getPage(wrs);
            assertEquals(/* FormValidation.OK */"<div/>", p.getWebResponse().getContentAsString());
        }
        Data data = site.getData();
        assertNotNull(data);
        assertEquals(new URL(url, "jenkins.war").toString(), data.core.url);
        assertEquals(new HashSet<String>(Arrays.asList("tasks", "dummy")), data.plugins.keySet());
        assertEquals(new URL(url, "tasks.jpi").toString(), data.plugins.get("tasks").url);
        assertEquals("http://nowhere.net/dummy.hpi", data.plugins.get("dummy").url);
    }

}
