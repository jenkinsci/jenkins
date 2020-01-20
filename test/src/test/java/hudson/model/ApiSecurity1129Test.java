package hudson.model;

import com.gargoylesoftware.htmlunit.WebResponse;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.xml.sax.SAXException;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

//TODO after the security fix, it could be merged inside ApiTest
public class ApiSecurity1129Test {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Test the wrapper parameter for the api/xml urls to avoid XSS.
     * @throws Exception See {@link #checkWrapperParam(String, Integer, String)}
     */
    @Issue("SECURITY-1129")
    @Test
    public void wrapperXss() throws Exception {
        String wrapper = "html%20xmlns=\"http://www.w3.org/1999/xhtml\"><script>alert(%27XSS%20Detected%27)</script></html><!--";

        checkWrapperParam(wrapper, HttpServletResponse.SC_BAD_REQUEST, Messages.Api_WrapperParamInvalid());
    }

    /**
     * Test the wrapper parameter for the api/xml urls with a bad name.
     * @throws Exception See {@link #checkWrapperParam(String, Integer, String)}
     */
    @Issue("SECURITY-1129")
    @Test
    public void wrapperBadName() throws Exception {
        String wrapper = "-badname";
        checkWrapperParam(wrapper, HttpServletResponse.SC_BAD_REQUEST, Messages.Api_WrapperParamInvalid());

    }

    /**
     * Test the wrapper parameter with a good name, to ensure the security fix doesn't break anything.
     * @throws Exception See {@link #checkWrapperParam(String, Integer, String)}
     */
    @Issue("SECURITY-1129")
    @Test
    public void wrapperGoodName() throws Exception {
        String wrapper = "__GoodName-..-OK";
        checkWrapperParam(wrapper, HttpServletResponse.SC_OK, null);

    }

    /**
     * Check the response for a XML api with the wrapper param specified. At least the statusCode or the responseMessage
     * should be indicated.
     * @param wrapper the wrapper param passed in the url.
     * @param statusCode the status code expected in the response. If it's null, it's not checked.
     * @param responseMessage the message expected in the response. If it's null, it's not checked.
     * @throws IOException See {@link org.jvnet.hudson.test.JenkinsRule.WebClient#goTo(String, String)}
     * @throws SAXException See {@link org.jvnet.hudson.test.JenkinsRule.WebClient#goTo(String, String)}
     */
    private void checkWrapperParam(String wrapper, Integer statusCode, String responseMessage) throws IOException, SAXException {
        if (statusCode == null && responseMessage == null) {
            fail("You should check at least one, the statusCode or the responseMessage when testing the wrapper param");
        }

        JenkinsRule.WebClient wc = j.createWebClient();
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);
        WebResponse response = wc.goTo(String.format("whoAmI/api/xml?xpath=*&wrapper=%s", wrapper), null).getWebResponse();

        if (response != null) {
            if (statusCode != null) {
                assertEquals(statusCode.intValue(), response.getStatusCode());
            }
            if (responseMessage != null) {
                assertEquals(responseMessage, response.getContentAsString());
            }
        } else {
            fail("The response shouldn't be null");
        }
    }
}
