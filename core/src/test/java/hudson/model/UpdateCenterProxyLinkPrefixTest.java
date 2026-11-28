package hudson.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UpdateCenterProxyLinkPrefixTest {

    @Test
    void nullRootUrlUsesServletContextPath() {
        assertHref(null, "", "/manage/configure");
        assertHref(null, "/jenkins", "/jenkins/manage/configure");
    }

    @Test
    void configuredRootUrlIsUnchanged() {
        String configured = "https://ci.example.com/jenkins/";
        assertEquals(configured, UpdateCenter.proxyConfigLinkPrefix(configured, "/ignored"));
        assertHref(configured, "", "https://ci.example.com/jenkins/manage/configure");

        String withoutTrailingSlash = "https://ci.example.com/jenkins";
        assertEquals(withoutTrailingSlash, UpdateCenter.proxyConfigLinkPrefix(withoutTrailingSlash, "/jenkins"));
    }

    private static void assertHref(String rootUrl, String contextPath, String expectedHref) {
        String prefix = UpdateCenter.proxyConfigLinkPrefix(rootUrl, contextPath);
        String connectionFailed = Messages.UpdateCenter_Status_ConnectionFailed("https://www.google.com/", prefix);
        String unknownHost = Messages.UpdateCenter_Status_UnknownHostException("updates.jenkins.io", prefix);
        String href = "href=\"" + expectedHref + "\"";
        assertTrue(connectionFailed.contains(href), connectionFailed);
        assertTrue(unknownHost.contains(href), unknownHost);
        assertFalse(connectionFailed.contains("href=\"null"), connectionFailed);
        assertFalse(unknownHost.contains("href=\"null"), unknownHost);
    }
}
