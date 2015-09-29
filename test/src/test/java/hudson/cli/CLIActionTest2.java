/*
 * The MIT License
 *
 * Copyright (c) 2015 Oleg Nenashev.
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

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebRequestSettings;
import com.gargoylesoftware.htmlunit.WebResponse;
import java.net.URL;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.PresetData;
import org.jvnet.hudson.test.recipes.PresetData.DataSet;

import static org.junit.Assert.*;
import org.jvnet.hudson.test.Issue;

/**
 * Tests for {@link CLIAction}.
 * CLIActionTest is being generated from Groovy, hence here we specify another one.
 * @author Oleg Nenashev
 */
public class CLIActionTest2 {
    
    @Rule
    public final JenkinsRule j = new JenkinsRule();
    
    @Test
    @PresetData(DataSet.NO_ANONYMOUS_READACCESS)
    @Issue("SECURITY-192")
    public void serveCliActionToAnonymousUser() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        
        // The behavior changed due to SECURITY-192. index page is no longer accessible to anonymous,
        // so we check the access by emulating the CLI connection post request
        WebRequestSettings settings = new WebRequestSettings(new URL(j.getURL(), "cli"));
        settings.setHttpMethod(HttpMethod.POST);
        settings.setAdditionalHeader("Session", UUID.randomUUID().toString());
        settings.setAdditionalHeader("Side", "download"); // We try to download something to init the duplex channel
        settings = wc.addCrumb(settings);
        
        Page page = wc.getPage(settings);
        WebResponse webResponse = page.getWebResponse();
        assertEquals("We expect that the proper POST request from CLI gets processed successfully", 
                200, webResponse.getStatusCode());
    }
}
