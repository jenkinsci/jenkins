/*
 * Copyright (c) 2008-2010 Yahoo! Inc.
 * All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.security.csrf;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.model.User;
import javax.servlet.http.HttpServletResponse;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

/**
 * @author dty
 */
public class DefaultCrumbIssuerSEC626Test { //TODO merge back to DefaultCrumbIssuerTest
    
    @Rule public JenkinsRule r = new JenkinsRule();

    @Before public void setIssuer() {
        r.jenkins.setCrumbIssuer(new DefaultCrumbIssuer(false));
    }

    @Test
    @Issue("SECURITY-626")
    public void crumbOnlyValidForOneSession() throws Exception {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        DefaultCrumbIssuer issuer = new DefaultCrumbIssuer(false);
        r.jenkins.setCrumbIssuer(issuer);

        User.getById("foo", true);

        DefaultCrumbIssuer.EXCLUDE_SESSION_ID = true;
        compareDifferentSessions_tokenAreEqual(true);

        DefaultCrumbIssuer.EXCLUDE_SESSION_ID = false;
        compareDifferentSessions_tokenAreEqual(false);
    }
    
    private void compareDifferentSessions_tokenAreEqual(boolean areEqual) throws Exception {
        WebClient wc = r.createWebClient();
        wc.login("foo");

        HtmlPage p = wc.goTo("configure");
        String crumb1 = p.getElementByName("Jenkins-Crumb").getAttribute("value");
        r.submit(p.getFormByName("config"));

        wc.goTo("logout");
        wc.login("foo");

        p = wc.goTo("configure");
        String crumb2 = p.getElementByName("Jenkins-Crumb").getAttribute("value");
        r.submit(p.getFormByName("config"));

        assertEquals(crumb1.equals(crumb2), areEqual);

        if (areEqual) {
            r.submit(p.getFormByName("config"));
        } else {
            replaceAllCrumbInPageBy(p, crumb1);
            try {
                // submit the form with previous session crumb
                r.submit(p.getFormByName("config"));
                fail();
            } catch (FailingHttpStatusCodeException e) {
                assertEquals(HttpServletResponse.SC_FORBIDDEN, e.getStatusCode());
                assertThat(e.getResponse().getContentAsString(), containsString("No valid crumb"));
            }
        }
    }

    private void replaceAllCrumbInPageBy(HtmlPage page, String newCrumb) {
        for (DomElement el : page.getElementsByName("Jenkins-Crumb")) {
            el.setAttribute("value", newCrumb);
        }
    }
}
