/*
 * Copyright (c) 2008-2010 Yahoo! Inc.
 * All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.security.csrf;

import com.gargoylesoftware.htmlunit.WebResponse;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 *
 * @author dty
 */
//TODO merge back to DefaultCrumbIssuerTest
public class DefaultCrumbIssuerSEC1704Test {
    
    @Rule public JenkinsRule r = new JenkinsRule();

    @Before public void setIssuer() {
        r.jenkins.setCrumbIssuer(new DefaultCrumbIssuer(false));
    }

    @Test
    @Issue("SECURITY-1704")
    public void custom_notExposedToIFrame() throws Exception {
        ensureXmlIsNotExposedToIFrame("crumbIssuer/");
        ensureJsonIsNotExposedToIFrame("crumbIssuer/");
        ensurePythonIsNotExposedToIFrame("crumbIssuer/");
    }

    private void ensureXmlIsNotExposedToIFrame(String itemUrl) throws Exception {
        WebResponse response = r.createWebClient().goTo(itemUrl + "api/xml", "application/xml").getWebResponse();
        assertThat(response.getResponseHeaderValue("X-Frame-Options"), equalTo("deny"));
    }

    private void ensureJsonIsNotExposedToIFrame(String itemUrl) throws Exception {
        WebResponse response = r.createWebClient().goTo(itemUrl + "api/json", "application/json").getWebResponse();
        assertThat(response.getResponseHeaderValue("X-Frame-Options"), equalTo("deny"));
    }

    private void ensurePythonIsNotExposedToIFrame(String itemUrl) throws Exception {
        WebResponse response = r.createWebClient().goTo(itemUrl + "api/python", "text/x-python").getWebResponse();
        assertThat(response.getResponseHeaderValue("X-Frame-Options"), equalTo("deny"));
    }
}
