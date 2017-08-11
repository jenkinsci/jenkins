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

/**
 * Created by haswell on 8/11/17.
 */
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
        given(jenkins.getRootUrl()).willReturn("http://localhost:8080");
        String referrer = proxySetupMonitor.buildRedirect("referrer", jenkins);
        System.out.println(referrer);
        String s = ReverseProxySetupMonitor.checkInput(URLDecoder.decode(referrer, "UTF-8"));
        String expected = "http://localhost:8080administrativeMonitor/hudson.diagnosis.ReverseProxySetupMonitor/testForReverseProxySetup/referrer/";
        assertThat(expected, is(s));
    }

    @Test(expected = HttpResponses.HttpResponseException.class)
    public void ensureIncorrectlyEncodedURLFails() {
        given(jenkins.getRootUrl()).willReturn("http://localhost:8080");
        String referrer = proxySetupMonitor.buildRedirect("referrer", jenkins);
        ReverseProxySetupMonitor.checkInput(referrer);

    }

}