package jenkins.util;

import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UrlHelperTest {

    @Test
    @Issue("JENKINS-31661")
    public void isValid() {
        assertTrue(UrlHelper.isValidRootUrl("http://www.google.com"));
        assertTrue(UrlHelper.isValidRootUrl("http://www.google.com/"));
        assertTrue(UrlHelper.isValidRootUrl("http://www.google.com/jenkins"));
        assertTrue(UrlHelper.isValidRootUrl("http://www.google.com:8080"));
        assertTrue(UrlHelper.isValidRootUrl("http://www.google.com:8080/jenkins"));
        assertTrue(UrlHelper.isValidRootUrl("https://www.google.com:8080/jenkins"));
        assertTrue(UrlHelper.isValidRootUrl("http://localhost:8080/jenkins"));
        assertTrue(UrlHelper.isValidRootUrl("http://localhost:8080/jenkins/"));
        assertTrue(UrlHelper.isValidRootUrl("http://my_server:8080/jenkins"));
        assertTrue(UrlHelper.isValidRootUrl("http://MY_SERVER_IN_PRIVATE_NETWORK:8080/jenkins"));
        assertTrue(UrlHelper.isValidRootUrl("http://jenkins"));
        
        assertFalse(UrlHelper.isValidRootUrl("http://jenkins::"));
        assertFalse(UrlHelper.isValidRootUrl("http://jenkins::80"));
        assertFalse(UrlHelper.isValidRootUrl("http//jenkins"));
        
        assertFalse(UrlHelper.isValidRootUrl("com."));
        assertFalse(UrlHelper.isValidRootUrl("http:// "));
        
        // examples not passing with a simple `new URL(url).toURI()` check
        assertFalse(UrlHelper.isValidRootUrl("http://jenkins//context"));
        assertFalse(UrlHelper.isValidRootUrl("http:/jenkins"));
        assertFalse(UrlHelper.isValidRootUrl("http://.com"));
        assertFalse(UrlHelper.isValidRootUrl("http://com."));
        assertFalse(UrlHelper.isValidRootUrl("http:/:"));
        assertFalse(UrlHelper.isValidRootUrl("http://..."));
        assertFalse(UrlHelper.isValidRootUrl("http://::::@example.com"));
        assertFalse(UrlHelper.isValidRootUrl("ftp://jenkins"));
        // this url will be used as a root url and so will be concatenated with other part, fragments are not allowed
        assertFalse(UrlHelper.isValidRootUrl("http://jenkins#fragment"));
    }
    
    @Test
    @Issue("JENKINS-51064")
    public void isAlsoValid() {
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
        assertFalse(UrlHelper.isValidRootUrl("http://jenkins.com."));
    
        // forbidden to have multiple dots in chain
        assertFalse(UrlHelper.isValidRootUrl("http://jen..kins.com"));
    }
}