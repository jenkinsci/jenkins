/*
 * The MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */


package hudson.diagnosis;

import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kohsuke.stapler.HttpResponses;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.BDDMockito.given;




@RunWith(MockitoJUnitRunner.class)
public class ReverseProxySetupMonitorTest {
  
    private ReverseProxySetupMonitor proxySetupMonitor;
    
    @Mock
    private Jenkins jenkins;
    
    
    @Before
    public void setUp() {
        proxySetupMonitor = new ReverseProxySetupMonitor();
    }
   
    

    @Test
    public void ensureSplittingIsCorrect() throws UnsupportedEncodingException {
        given(jenkins.getRootUrl()).willReturn("http://localhost:8080/");
        String referrer = proxySetupMonitor.buildRedirect("referrer", jenkins);
        String s = ReverseProxySetupMonitor.checkInput(URLDecoder.decode(referrer, "UTF-8"));
        String expected = "http://localhost:8080/administrativeMonitor/hudson.diagnosis.ReverseProxySetupMonitor/testForReverseProxySetup/referrer/";
        assertThat(expected, is(s));
    }

    @Test(expected = HttpResponses.HttpResponseException.class)
    public void ensureIncorrectlyEncodedURLFails() {
        given(jenkins.getRootUrl()).willReturn("http://localhost:8080/");
        String referrer = proxySetupMonitor.buildRedirect("referrer", jenkins);
        ReverseProxySetupMonitor.checkInput(referrer);

    }

}