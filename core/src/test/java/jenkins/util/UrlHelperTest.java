package jenkins.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

class UrlHelperTest {

    @Test
    @Issue("JENKINS-31661")
    void regularCases() {
        assertTrue(UrlHelper.isValidRootUrl("http://www.google.com"));
        // trailing slash is optional
        assertTrue(UrlHelper.isValidRootUrl("http://www.google.com/"));
        // path is allowed
        assertTrue(UrlHelper.isValidRootUrl("http://www.google.com/jenkins"));
        // port is allowed to be precised
        assertTrue(UrlHelper.isValidRootUrl("http://www.google.com:8080"));
        assertTrue(UrlHelper.isValidRootUrl("http://www.google.com:8080/jenkins"));
        // http or https are only valid schemes
        assertTrue(UrlHelper.isValidRootUrl("https://www.google.com:8080/jenkins"));
        // also with their UPPERCASE equivalent
        assertTrue(UrlHelper.isValidRootUrl("HTTP://www.google.com:8080/jenkins"));
        assertTrue(UrlHelper.isValidRootUrl("HTTPS://www.google.com:8080/jenkins"));

        assertTrue(UrlHelper.isValidRootUrl("http://localhost:8080/jenkins"));
        assertTrue(UrlHelper.isValidRootUrl("http://localhost:8080/jenkins/"));
        assertTrue(UrlHelper.isValidRootUrl("http://my_server:8080/jenkins"));
        assertTrue(UrlHelper.isValidRootUrl("http://MY_SERVER_IN_PRIVATE_NETWORK:8080/jenkins"));
        assertTrue(UrlHelper.isValidRootUrl("http://jenkins"));
        assertTrue(UrlHelper.isValidRootUrl("http://j"));
        assertTrue(UrlHelper.isValidRootUrl("http://j.io"));

        assertFalse(UrlHelper.isValidRootUrl("http://jenkins::"));
        assertFalse(UrlHelper.isValidRootUrl("http://jenkins::80"));
        // scheme must be correctly spelled (missing :)
        assertFalse(UrlHelper.isValidRootUrl("http//jenkins"));

        // scheme is mandatory
        assertFalse(UrlHelper.isValidRootUrl("com."));
        // spaces are forbidden
        assertFalse(UrlHelper.isValidRootUrl("http:// "));

        // examples not passing with a simple `new URL(url).toURI()` check
        assertFalse(UrlHelper.isValidRootUrl("http://jenkins//context"));
        assertFalse(UrlHelper.isValidRootUrl("http:/jenkins"));
        assertFalse(UrlHelper.isValidRootUrl("http://.com"));
        assertFalse(UrlHelper.isValidRootUrl("http:/:"));
        assertFalse(UrlHelper.isValidRootUrl("http://..."));
        assertFalse(UrlHelper.isValidRootUrl("http://::::@example.com"));
        assertFalse(UrlHelper.isValidRootUrl("ftp://jenkins"));
    }

    @Test
    void fragmentIsForbidden() {
        // this url will be used as a root url and so will be concatenated with other part, fragment part is not allowed
        assertFalse(UrlHelper.isValidRootUrl("http://jenkins#fragment"));
        assertFalse(UrlHelper.isValidRootUrl("http://jenkins.com#fragment"));
    }

    @Test
    void queryIsForbidden() {
        // this url will be used as a root url and so will be concatenated with other part, query part is not allowed
        assertFalse(UrlHelper.isValidRootUrl("http://jenkins?param=test"));
        assertFalse(UrlHelper.isValidRootUrl("http://jenkins.com?param=test"));
    }

    @Test
    void otherCharactersAreForbidden() {
        // other characters are not allowed
        assertFalse(UrlHelper.isValidRootUrl("http://jenk@ins.com"));
        assertFalse(UrlHelper.isValidRootUrl("http://jenk(ins.com"));
        assertFalse(UrlHelper.isValidRootUrl("http://jenk)ins.com"));
        assertFalse(UrlHelper.isValidRootUrl("http://jenk[ins.com"));
        assertFalse(UrlHelper.isValidRootUrl("http://jenk]ins.com"));
        assertFalse(UrlHelper.isValidRootUrl("http://jenk%ins.com"));
        assertFalse(UrlHelper.isValidRootUrl("http://jenk$ins.com"));
        assertFalse(UrlHelper.isValidRootUrl("http://jenk!ins.com"));
        assertFalse(UrlHelper.isValidRootUrl("http://jenk?ins.com"));
    }

    @Test
    void ipv4Allowed() {
        assertTrue(UrlHelper.isValidRootUrl("http://172.52.125.12"));
        assertTrue(UrlHelper.isValidRootUrl("http://172.52.125.12/jenkins"));
        assertTrue(UrlHelper.isValidRootUrl("http://172.52.125.12:8080"));
        assertTrue(UrlHelper.isValidRootUrl("http://172.52.125.12:8080/jenkins"));
    }

    @Test
    void ipv6Allowed() {
        assertTrue(UrlHelper.isValidRootUrl("http://[FEDC:BA98:7654:3210:FEDC:BA98:7654:3210]"));
        assertTrue(UrlHelper.isValidRootUrl("http://[FEDC:0000:0000:3210:FEDC:BA98:7654:3210]"));
        // 0000 can be reduced to 0
        assertTrue(UrlHelper.isValidRootUrl("http://[FEDC:0:0:3210:FEDC:BA98:7654:3210]"));
        // an unique sequence of multiple fragments with 0's could be omitted completely
        assertTrue(UrlHelper.isValidRootUrl("http://[FEDC::3210:FEDC:BA98:7654:3210]"));
        // but only one sequence
        assertFalse(UrlHelper.isValidRootUrl("http://[2001::85a3::ac1f]"));

        // port and path are still allowed
        assertTrue(UrlHelper.isValidRootUrl("http://[FEDC:0:0:3210:FEDC:BA98:7654:3210]:8001/jenkins"));
        assertTrue(UrlHelper.isValidRootUrl("http://[FEDC:0:0:3210:FEDC:BA98:7654:3210]:8001"));
        assertTrue(UrlHelper.isValidRootUrl("http://[FEDC:0:0:3210:FEDC:BA98:7654:3210]/jenkins"));
        // dashes are not allowed inside ipv6
        assertFalse(UrlHelper.isValidRootUrl("http://[FEDC:0:0:32-10:FEDC:BA98:7654:3210]:8001/jenkins"));
        assertFalse(UrlHelper.isValidRootUrl("http://[FEDC:0:0:3210:-FEDC:BA98:7654:3210]:8001/jenkins"));
    }

    @Test
    @Issue("JENKINS-51064")
    void withCustomDomain() {
        assertTrue(UrlHelper.isValidRootUrl("http://my-server:8080/jenkins"));
        assertTrue(UrlHelper.isValidRootUrl("http://jenkins.internal/"));
        assertTrue(UrlHelper.isValidRootUrl("http://jenkins.otherDomain/"));
        assertTrue(UrlHelper.isValidRootUrl("http://my-server.domain:8080/jenkins"));
        assertTrue(UrlHelper.isValidRootUrl("http://my-ser_ver.do_m-ain:8080/jenkins"));
        assertTrue(UrlHelper.isValidRootUrl("http://my-ser_ver.do_m-ain:8080/jenkins"));

        // forbidden to start or end domain with - or .
        assertFalse(UrlHelper.isValidRootUrl("http://-jenkins.com"));
        assertFalse(UrlHelper.isValidRootUrl("http://jenkins.com-"));
        assertFalse(UrlHelper.isValidRootUrl("http://.jenkins.com"));

        // allowed to have multiple dots in chain
        assertTrue(UrlHelper.isValidRootUrl("http://jen..kins.com"));
    }

    @Test
    void multipleConsecutiveDashesAreAllowed() {
        assertTrue(UrlHelper.isValidRootUrl("http://jenk--ins.internal/"));
        assertTrue(UrlHelper.isValidRootUrl("http://www.go-----ogle.com/"));
        // even with subdomain being just a dash
        assertTrue(UrlHelper.isValidRootUrl("http://www.go.-.--.--ogle.com/"));
    }

    @Test
    @Issue("JENKINS-51158")
    void trailingDotsAreAccepted() {
        assertTrue(UrlHelper.isValidRootUrl("http://jenkins.internal./"));
        assertTrue(UrlHelper.isValidRootUrl("http://jenkins.internal......./"));
        assertTrue(UrlHelper.isValidRootUrl("http://my-server.domain.:8080/jenkins"));
        assertTrue(UrlHelper.isValidRootUrl("http://my-server.domain......:8080/jenkins"));
        assertTrue(UrlHelper.isValidRootUrl("http://jenkins.com."));
        assertTrue(UrlHelper.isValidRootUrl("http://jenkins.com......"));
    }

    @Test
    @Issue("SECURITY-1471")
    void ensureJavaScriptSchemaIsNotAllowed() {
        assertFalse(UrlHelper.isValidRootUrl("javascript:alert(123)"));
    }
}
