/*
 * Copyright (c) 2026
 * All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://opensource.org/licenses/mit-license.php)
 */

package hudson.security.csrf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.WebApp;

/**
 * Simulates the startup window reported in issue #11669 (JENKINS-63385): the
 * Jenkins instance exists and the crumb issuer is configured, but the crumb
 * issuer descriptor is not yet registered as an extension. Covers both the
 * filter path and the Stapler crumb issuance/validation paths.
 */
@WithJenkins
class CrumbFilterStartupRaceTest {

    private JenkinsRule r;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        r = rule;
        r.jenkins.setCrumbIssuer(new DefaultCrumbIssuer());
        // simulate the startup window: instance is up, extensions not yet registered
        var list = r.jenkins.getDescriptorList(CrumbIssuer.class);
        list.remove(r.jenkins.getDescriptor(DefaultCrumbIssuer.class));
    }

    @Test
    @Issue("https://github.com/jenkinsci/jenkins/issues/11669")
    void descriptorAccessFailsWhileExtensionsAreNotRegistered() {
        CrumbIssuer issuer = r.jenkins.getCrumbIssuer();
        assertThrows(AssertionError.class, issuer::getDescriptor);
    }

    @Test
    @Issue("https://github.com/jenkinsci/jenkins/issues/11669")
    void postRequestDoesNotBlowUpWhileExtensionsAreNotRegistered() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("POST");
        when(req.getRequestURI()).thenReturn("/jenkins/createItem");
        when(req.getContextPath()).thenReturn("/jenkins");
        when(req.getParameterNames()).thenReturn(Collections.<String>emptyEnumeration());

        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        // must not propagate "DefaultCrumbIssuer is missing its descriptor"
        new CrumbFilter().doFilter(req, res, chain);

        // fail closed: the request is rejected, not passed through unchecked
        verify(res).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @Issue("https://github.com/jenkinsci/jenkins/issues/11669")
    void staplerIssuanceFallsBackWhileDescriptorIsNotRegistered() {
        org.kohsuke.stapler.CrumbIssuer staplerIssuer = WebApp.get(r.jenkins.getServletContext()).getCrumbIssuer();
        assertNotNull(staplerIssuer);

        StaplerRequest2 req = mock(StaplerRequest2.class);
        // must not propagate "DefaultCrumbIssuer is missing its descriptor":
        // falls back to the NONE issuer, which issues an empty crumb
        assertEquals("", staplerIssuer.issueCrumb(req));
    }

    @Test
    @Issue("https://github.com/jenkinsci/jenkins/issues/11669")
    void staplerValidationRejectsCrumbWhileDescriptorIsNotRegistered() {
        org.kohsuke.stapler.CrumbIssuer staplerIssuer = WebApp.get(r.jenkins.getServletContext()).getCrumbIssuer();

        StaplerRequest2 req = mock(StaplerRequest2.class);
        // even the empty crumb issued by the fallback above must be rejected,
        // not accepted and not propagated as AssertionError
        assertThrows(SecurityException.class, () -> staplerIssuer.validateCrumb(req, ""));
    }
}
