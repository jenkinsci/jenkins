package jenkins.util;

import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Issue("JENKINS-44052")
public class UrlHelperTest {

    @Test
    public void isValid() {
        assertTrue(UrlHelper.isValid("http://www.google.com"));
        assertTrue(UrlHelper.isValid("http://www.google.com/jenkins"));
        assertTrue(UrlHelper.isValid("http://www.google.com:8080"));
        assertTrue(UrlHelper.isValid("http://www.google.com:8080/jenkins"));
        assertTrue(UrlHelper.isValid("https://www.google.com:8080/jenkins"));
        assertTrue(UrlHelper.isValid("http://localhost:8080/jenkins"));
        assertTrue(UrlHelper.isValid("http://my_server:8080/jenkins"));
        assertTrue(UrlHelper.isValid("http://MY_SERVER_IN_PRIVATE_NETWORK:8080/jenkins"));
        assertTrue(UrlHelper.isValid("http://jenkins"));
        
        assertFalse(UrlHelper.isValid("http://jenkins::"));
        assertFalse(UrlHelper.isValid("http://jenkins::80"));
        assertFalse(UrlHelper.isValid("http://jenkins//context"));
        assertFalse(UrlHelper.isValid("http:/jenkins"));
        assertFalse(UrlHelper.isValid("http//jenkins"));
        
        assertFalse(UrlHelper.isValid("ftp://jenkins"));
    }

}