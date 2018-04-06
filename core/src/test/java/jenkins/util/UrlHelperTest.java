package jenkins.util;

import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Issue("JENKINS-31661")
public class UrlHelperTest {

    @Test
    public void isValid() {
        assertTrue(UrlHelper.isValidRootUrl("http://www.google.com"));
        assertTrue(UrlHelper.isValidRootUrl("http://www.google.com/jenkins"));
        assertTrue(UrlHelper.isValidRootUrl("http://www.google.com:8080"));
        assertTrue(UrlHelper.isValidRootUrl("http://www.google.com:8080/jenkins"));
        assertTrue(UrlHelper.isValidRootUrl("https://www.google.com:8080/jenkins"));
        assertTrue(UrlHelper.isValidRootUrl("http://localhost:8080/jenkins"));
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

}