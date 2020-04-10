package jenkins.util;

import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CustomUrlValidatorTest {

    @Test
    @Issue("JENKINS-31661")
    public void regularCases() {
        assertTrue(new CustomUrlValidator().isValid("http://www.google.com"));
        // trailing slash is optional
        assertTrue(new CustomUrlValidator().isValid("http://www.google.com/"));
        // path is allowed
        assertTrue(new CustomUrlValidator().isValid("http://www.google.com/jenkins"));
        // port is allowed to be precised
        assertTrue(new CustomUrlValidator().isValid("http://www.google.com:8080"));
        assertTrue(new CustomUrlValidator().isValid("http://www.google.com:8080/jenkins"));
        // http or https are only valid schemes
        assertTrue(new CustomUrlValidator().isValid("https://www.google.com:8080/jenkins"));
        // also with their UPPERCASE equivalent
        assertTrue(new CustomUrlValidator().isValid("HTTP://www.google.com:8080/jenkins"));
        assertTrue(new CustomUrlValidator().isValid("HTTPS://www.google.com:8080/jenkins"));

        assertTrue(new CustomUrlValidator().isValid("http://localhost:8080/jenkins"));
        assertTrue(new CustomUrlValidator().isValid("http://localhost:8080/jenkins/"));
        assertTrue(new CustomUrlValidator().isValid("http://my_server:8080/jenkins"));
        assertTrue(new CustomUrlValidator().isValid("http://MY_SERVER_IN_PRIVATE_NETWORK:8080/jenkins"));
        assertTrue(new CustomUrlValidator().isValid("http://jenkins"));
        assertTrue(new CustomUrlValidator().isValid("http://j"));
        assertTrue(new CustomUrlValidator().isValid("http://j.io"));

        assertFalse(new CustomUrlValidator().isValid("http://jenkins::"));
        assertFalse(new CustomUrlValidator().isValid("http://jenkins::80"));
        // scheme must be correctly spelled (missing :)
        assertFalse(new CustomUrlValidator().isValid("http//jenkins"));

        // scheme is mandatory
        assertFalse(new CustomUrlValidator().isValid("com."));
        // spaces are forbidden
        assertFalse(new CustomUrlValidator().isValid("http:// "));

        // examples not passing with a simple `new URL(url).toURI()` check
        assertFalse(new CustomUrlValidator().isValid("http://jenkins//context"));
        assertFalse(new CustomUrlValidator().isValid("http:/jenkins"));
        assertFalse(new CustomUrlValidator().isValid("http://.com"));
        assertFalse(new CustomUrlValidator().isValid("http:/:"));
        assertFalse(new CustomUrlValidator().isValid("http://..."));
        assertFalse(new CustomUrlValidator().isValid("http://::::@example.com"));
        assertFalse(new CustomUrlValidator().isValid("ftp://jenkins"));
    }
    
    @Test
    public void fragmentIsForbidden(){
        // this url will be used as a root url and so will be concatenated with other part, fragment part is not allowed
        assertFalse(new CustomUrlValidator().isValid("http://jenkins#fragment"));
        assertFalse(new CustomUrlValidator().isValid("http://jenkins.com#fragment"));
    }
    
    @Test
    public void queryIsForbidden(){
        // this url will be used as a root url and so will be concatenated with other part, query part is not allowed
        assertFalse(new CustomUrlValidator().isValid("http://jenkins?param=test"));
        assertFalse(new CustomUrlValidator().isValid("http://jenkins.com?param=test"));
    }
    
    @Test
    public void otherCharactersAreForbidden(){
        // other characters are not allowed
        assertFalse(new CustomUrlValidator().isValid("http://jenk@ins.com"));
        assertFalse(new CustomUrlValidator().isValid("http://jenk(ins.com"));
        assertFalse(new CustomUrlValidator().isValid("http://jenk)ins.com"));
        assertFalse(new CustomUrlValidator().isValid("http://jenk[ins.com"));
        assertFalse(new CustomUrlValidator().isValid("http://jenk]ins.com"));
        assertFalse(new CustomUrlValidator().isValid("http://jenk%ins.com"));
        assertFalse(new CustomUrlValidator().isValid("http://jenk$ins.com"));
        assertFalse(new CustomUrlValidator().isValid("http://jenk!ins.com"));
        assertFalse(new CustomUrlValidator().isValid("http://jenk?ins.com"));
    }
    
    @Test
    public void ipv4Allowed(){
        assertTrue(new CustomUrlValidator().isValid("http://172.52.125.12"));
        assertTrue(new CustomUrlValidator().isValid("http://172.52.125.12/jenkins"));
        assertTrue(new CustomUrlValidator().isValid("http://172.52.125.12:8080"));
        assertTrue(new CustomUrlValidator().isValid("http://172.52.125.12:8080/jenkins"));
    }
    
    @Test
    public void ipv6Allowed() {
        assertTrue(new CustomUrlValidator().isValid("http://[FEDC:BA98:7654:3210:FEDC:BA98:7654:3210]"));
        assertTrue(new CustomUrlValidator().isValid("http://[FEDC:0000:0000:3210:FEDC:BA98:7654:3210]"));
        // 0000 can be reduced to 0
        assertTrue(new CustomUrlValidator().isValid("http://[FEDC:0:0:3210:FEDC:BA98:7654:3210]"));
        // an unique sequence of multiple fragments with 0's could be omitted completely
        assertTrue(new CustomUrlValidator().isValid("http://[FEDC::3210:FEDC:BA98:7654:3210]"));
        // but only one sequence
        assertFalse(new CustomUrlValidator().isValid("http://[2001::85a3::ac1f]"));
        
        // port and path are still allowed
        assertTrue(new CustomUrlValidator().isValid("http://[FEDC:0:0:3210:FEDC:BA98:7654:3210]:8001/jenkins"));
        assertTrue(new CustomUrlValidator().isValid("http://[FEDC:0:0:3210:FEDC:BA98:7654:3210]:8001"));
        assertTrue(new CustomUrlValidator().isValid("http://[FEDC:0:0:3210:FEDC:BA98:7654:3210]/jenkins"));
        // dashes are not allowed inside ipv6
        assertFalse(new CustomUrlValidator().isValid("http://[FEDC:0:0:32-10:FEDC:BA98:7654:3210]:8001/jenkins"));
        assertFalse(new CustomUrlValidator().isValid("http://[FEDC:0:0:3210:-FEDC:BA98:7654:3210]:8001/jenkins"));
    }
    
    @Test
    @Issue("JENKINS-51064")
    public void withCustomDomain() {
        assertTrue(new CustomUrlValidator().isValid("http://my-server:8080/jenkins"));
        assertTrue(new CustomUrlValidator().isValid("http://jenkins.internal/"));
        assertTrue(new CustomUrlValidator().isValid("http://jenkins.otherDomain/"));
        assertTrue(new CustomUrlValidator().isValid("http://my-server.domain:8080/jenkins"));
        assertTrue(new CustomUrlValidator().isValid("http://my-ser_ver.do_m-ain:8080/jenkins"));
        assertTrue(new CustomUrlValidator().isValid("http://my-ser_ver.do_m-ain:8080/jenkins"));
        
        // forbidden to start or end domain with - or .
        assertFalse(new CustomUrlValidator().isValid("http://-jenkins.com"));
        assertFalse(new CustomUrlValidator().isValid("http://jenkins.com-"));
        assertFalse(new CustomUrlValidator().isValid("http://.jenkins.com"));
        
        // allowed to have multiple dots in chain
        assertTrue(new CustomUrlValidator().isValid("http://jen..kins.com"));
    }
    
    @Test
    public void multipleConsecutiveDashesAreAllowed() {
        assertTrue(new CustomUrlValidator().isValid("http://jenk--ins.internal/"));
        assertTrue(new CustomUrlValidator().isValid("http://www.go-----ogle.com/"));
        // even with subdomain being just a dash
        assertTrue(new CustomUrlValidator().isValid("http://www.go.-.--.--ogle.com/"));
    }
    
    @Test
    @Issue("JENKINS-51158")
    public void trailingDotsAreAccepted() {
        assertTrue(new CustomUrlValidator().isValid("http://jenkins.internal./"));
        assertTrue(new CustomUrlValidator().isValid("http://jenkins.internal......./"));
        assertTrue(new CustomUrlValidator().isValid("http://my-server.domain.:8080/jenkins"));
        assertTrue(new CustomUrlValidator().isValid("http://my-server.domain......:8080/jenkins"));
        assertTrue(new CustomUrlValidator().isValid("http://jenkins.com."));
        assertTrue(new CustomUrlValidator().isValid("http://jenkins.com......"));
    }

    @Test
    @Issue("SECURITY-1471")
    public void ensureJavascriptSchemaIsNotAllowed() {
        assertFalse(new CustomUrlValidator().isValid("javascript:alert(123)"));
    }
}
