package jenkins.util;

import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CustomUrlValidatorTest {

    @Test
    @Issue("JENKINS-31661")
    public void regularCases() {
        assertTrue(CustomUrlValidator.isValidRootUrl("http://www.google.com"));
        // trailing slash is optional
        assertTrue(CustomUrlValidator.isValidRootUrl("http://www.google.com/"));
        // path is allowed
        assertTrue(CustomUrlValidator.isValidRootUrl("http://www.google.com/jenkins"));
        // port is allowed to be precised
        assertTrue(CustomUrlValidator.isValidRootUrl("http://www.google.com:8080"));
        assertTrue(CustomUrlValidator.isValidRootUrl("http://www.google.com:8080/jenkins"));
        // http or https are only valid schemes
        assertTrue(CustomUrlValidator.isValidRootUrl("https://www.google.com:8080/jenkins"));
        // also with their UPPERCASE equivalent
        assertTrue(CustomUrlValidator.isValidRootUrl("HTTP://www.google.com:8080/jenkins"));
        assertTrue(CustomUrlValidator.isValidRootUrl("HTTPS://www.google.com:8080/jenkins"));

        assertTrue(CustomUrlValidator.isValidRootUrl("http://localhost:8080/jenkins"));
        assertTrue(CustomUrlValidator.isValidRootUrl("http://localhost:8080/jenkins/"));
        assertTrue(CustomUrlValidator.isValidRootUrl("http://my_server:8080/jenkins"));
        assertTrue(CustomUrlValidator.isValidRootUrl("http://MY_SERVER_IN_PRIVATE_NETWORK:8080/jenkins"));
        assertTrue(CustomUrlValidator.isValidRootUrl("http://jenkins"));
        assertTrue(CustomUrlValidator.isValidRootUrl("http://j"));
        assertTrue(CustomUrlValidator.isValidRootUrl("http://j.io"));

        assertFalse(CustomUrlValidator.isValidRootUrl("http://jenkins::"));
        assertFalse(CustomUrlValidator.isValidRootUrl("http://jenkins::80"));
        // scheme must be correctly spelled (missing :)
        assertFalse(CustomUrlValidator.isValidRootUrl("http//jenkins"));

        // scheme is mandatory
        assertFalse(CustomUrlValidator.isValidRootUrl("com."));
        // spaces are forbidden
        assertFalse(CustomUrlValidator.isValidRootUrl("http:// "));

        // examples not passing with a simple `new URL(url).toURI()` check
        assertFalse(CustomUrlValidator.isValidRootUrl("http://jenkins//context"));
        assertFalse(CustomUrlValidator.isValidRootUrl("http:/jenkins"));
        assertFalse(CustomUrlValidator.isValidRootUrl("http://.com"));
        assertFalse(CustomUrlValidator.isValidRootUrl("http:/:"));
        assertFalse(CustomUrlValidator.isValidRootUrl("http://..."));
        assertFalse(CustomUrlValidator.isValidRootUrl("http://::::@example.com"));
        assertFalse(CustomUrlValidator.isValidRootUrl("ftp://jenkins"));
    }
    
    @Test
    public void fragmentIsForbidden(){
        // this url will be used as a root url and so will be concatenated with other part, fragment part is not allowed
        assertFalse(CustomUrlValidator.isValidRootUrl("http://jenkins#fragment"));
        assertFalse(CustomUrlValidator.isValidRootUrl("http://jenkins.com#fragment"));
    }
    
    @Test
    public void queryIsForbidden(){
        // this url will be used as a root url and so will be concatenated with other part, query part is not allowed
        assertFalse(CustomUrlValidator.isValidRootUrl("http://jenkins?param=test"));
        assertFalse(CustomUrlValidator.isValidRootUrl("http://jenkins.com?param=test"));
    }
    
    @Test
    public void otherCharactersAreForbidden(){
        // other characters are not allowed
        assertFalse(CustomUrlValidator.isValidRootUrl("http://jenk@ins.com"));
        assertFalse(CustomUrlValidator.isValidRootUrl("http://jenk(ins.com"));
        assertFalse(CustomUrlValidator.isValidRootUrl("http://jenk)ins.com"));
        assertFalse(CustomUrlValidator.isValidRootUrl("http://jenk[ins.com"));
        assertFalse(CustomUrlValidator.isValidRootUrl("http://jenk]ins.com"));
        assertFalse(CustomUrlValidator.isValidRootUrl("http://jenk%ins.com"));
        assertFalse(CustomUrlValidator.isValidRootUrl("http://jenk$ins.com"));
        assertFalse(CustomUrlValidator.isValidRootUrl("http://jenk!ins.com"));
        assertFalse(CustomUrlValidator.isValidRootUrl("http://jenk?ins.com"));
    }
    
    @Test
    public void ipv4Allowed(){
        assertTrue(CustomUrlValidator.isValidRootUrl("http://172.52.125.12"));
        assertTrue(CustomUrlValidator.isValidRootUrl("http://172.52.125.12/jenkins"));
        assertTrue(CustomUrlValidator.isValidRootUrl("http://172.52.125.12:8080"));
        assertTrue(CustomUrlValidator.isValidRootUrl("http://172.52.125.12:8080/jenkins"));
    }
    
    @Test
    public void ipv6Allowed() {
        assertTrue(CustomUrlValidator.isValidRootUrl("http://[FEDC:BA98:7654:3210:FEDC:BA98:7654:3210]"));
        assertTrue(CustomUrlValidator.isValidRootUrl("http://[FEDC:0000:0000:3210:FEDC:BA98:7654:3210]"));
        // 0000 can be reduced to 0
        assertTrue(CustomUrlValidator.isValidRootUrl("http://[FEDC:0:0:3210:FEDC:BA98:7654:3210]"));
        // an unique sequence of multiple fragments with 0's could be omitted completely
        assertTrue(CustomUrlValidator.isValidRootUrl("http://[FEDC::3210:FEDC:BA98:7654:3210]"));
        // but only one sequence
        assertFalse(CustomUrlValidator.isValidRootUrl("http://[2001::85a3::ac1f]"));
        
        // port and path are still allowed
        assertTrue(CustomUrlValidator.isValidRootUrl("http://[FEDC:0:0:3210:FEDC:BA98:7654:3210]:8001/jenkins"));
        assertTrue(CustomUrlValidator.isValidRootUrl("http://[FEDC:0:0:3210:FEDC:BA98:7654:3210]:8001"));
        assertTrue(CustomUrlValidator.isValidRootUrl("http://[FEDC:0:0:3210:FEDC:BA98:7654:3210]/jenkins"));
        // dashes are not allowed inside ipv6
        assertFalse(CustomUrlValidator.isValidRootUrl("http://[FEDC:0:0:32-10:FEDC:BA98:7654:3210]:8001/jenkins"));
        assertFalse(CustomUrlValidator.isValidRootUrl("http://[FEDC:0:0:3210:-FEDC:BA98:7654:3210]:8001/jenkins"));
    }
    
    @Test
    @Issue("JENKINS-51064")
    public void withCustomDomain() {
        assertTrue(CustomUrlValidator.isValidRootUrl("http://my-server:8080/jenkins"));
        assertTrue(CustomUrlValidator.isValidRootUrl("http://jenkins.internal/"));
        assertTrue(CustomUrlValidator.isValidRootUrl("http://jenkins.otherDomain/"));
        assertTrue(CustomUrlValidator.isValidRootUrl("http://my-server.domain:8080/jenkins"));
        assertTrue(CustomUrlValidator.isValidRootUrl("http://my-ser_ver.do_m-ain:8080/jenkins"));
        assertTrue(CustomUrlValidator.isValidRootUrl("http://my-ser_ver.do_m-ain:8080/jenkins"));
        
        // forbidden to start or end domain with - or .
        assertFalse(CustomUrlValidator.isValidRootUrl("http://-jenkins.com"));
        assertFalse(CustomUrlValidator.isValidRootUrl("http://jenkins.com-"));
        assertFalse(CustomUrlValidator.isValidRootUrl("http://.jenkins.com"));
        
        // allowed to have multiple dots in chain
        assertTrue(CustomUrlValidator.isValidRootUrl("http://jen..kins.com"));
    }
    
    @Test
    public void multipleConsecutiveDashesAreAllowed() {
        assertTrue(CustomUrlValidator.isValidRootUrl("http://jenk--ins.internal/"));
        assertTrue(CustomUrlValidator.isValidRootUrl("http://www.go-----ogle.com/"));
        // even with subdomain being just a dash
        assertTrue(CustomUrlValidator.isValidRootUrl("http://www.go.-.--.--ogle.com/"));
    }
    
    @Test
    @Issue("JENKINS-51158")
    public void trailingDotsAreAccepted() {
        assertTrue(CustomUrlValidator.isValidRootUrl("http://jenkins.internal./"));
        assertTrue(CustomUrlValidator.isValidRootUrl("http://jenkins.internal......./"));
        assertTrue(CustomUrlValidator.isValidRootUrl("http://my-server.domain.:8080/jenkins"));
        assertTrue(CustomUrlValidator.isValidRootUrl("http://my-server.domain......:8080/jenkins"));
        assertTrue(CustomUrlValidator.isValidRootUrl("http://jenkins.com."));
        assertTrue(CustomUrlValidator.isValidRootUrl("http://jenkins.com......"));
    }

    @Test
    @Issue("SECURITY-1471")
    public void ensureJavascriptSchemaIsNotAllowed() {
        assertFalse(CustomUrlValidator.isValidRootUrl("javascript:alert(123)"));
    }
}
